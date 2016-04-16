package br.com.autonomiccs.autonomic.plugin.common.guru;

import java.util.List;

import org.apache.cloudstack.config.ApiServiceConfiguration;
import org.apache.cloudstack.framework.config.dao.ConfigurationDao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.cloud.agent.api.Answer;
import com.cloud.agent.manager.Commands;
import com.cloud.configuration.Config;
import com.cloud.dc.DataCenter;
import com.cloud.dc.DataCenter.NetworkType;
import com.cloud.dc.DataCenterVO;
import com.cloud.dc.dao.DataCenterDao;
import com.cloud.deploy.DeployDestination;
import com.cloud.exception.ResourceUnavailableException;
import com.cloud.network.Networks.TrafficType;
import com.cloud.utils.net.NetUtils;
import com.cloud.vm.NicProfile;
import com.cloud.vm.ReservationContext;
import com.cloud.vm.VirtualMachine;
import com.cloud.vm.VirtualMachineGuru;
import com.cloud.vm.VirtualMachineManager;
import com.cloud.vm.VirtualMachineProfile;

import br.com.autonomiccs.autonomic.plugin.common.daos.CleverCloudSystemVmDao;
import br.com.autonomiccs.autonomic.plugin.common.pojos.CleverCloudsSystemVm;

@Component
public class ClevercloudsSystemVmsGuru implements VirtualMachineGuru, InitializingBean {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    @Autowired
    private VirtualMachineManager virtualMachineManager;

    @Autowired
    private CleverCloudSystemVmDao cleverCloudSystemVmDao;

    @Autowired
    private ConfigurationDao configurationDao;

    @Autowired
    private DataCenterDao datacenterDao;

    @Override
    public void afterPropertiesSet() throws Exception {
        logger.debug("Clever clouds system VMs guru initialized.");
        virtualMachineManager.registerGuru(VirtualMachine.Type.Instance, this);
    }

    /**
     * For now we do not do anything here, shit design
     */
    @Override
    public boolean finalizeCommandsOnStart(Commands arg0, VirtualMachineProfile arg1) {
        return true;
    }

    @Override
    public boolean finalizeDeployment(Commands cmds, VirtualMachineProfile profile, DeployDestination dest, ReservationContext context) throws ResourceUnavailableException {
        CleverCloudsSystemVm cleverCloudsSystemVm = cleverCloudSystemVmDao.findById(profile.getId());

        DataCenter dc = dest.getDataCenter();
        List<NicProfile> nics = profile.getNics();
        for (NicProfile nic : nics) {
            if (nic.getTrafficType() == TrafficType.Management) {
                cleverCloudsSystemVm.setManagementIpAddress(nic.getIPv4Address());
                continue;
            }
            if (nic.getTrafficType() == TrafficType.Control) {
                cleverCloudsSystemVm.setPrivateIpAddress(nic.getIPv4Address());
                continue;
            }
            if ((nic.getTrafficType() == TrafficType.Public && dc.getNetworkType() == NetworkType.Advanced)
                    || (nic.getTrafficType() == TrafficType.Guest && (dc.getNetworkType() == NetworkType.Basic || dc.isSecurityGroupEnabled()))) {
                cleverCloudsSystemVm.setPublicIpAddress(nic.getIPv4Address());
            }
        }
        cleverCloudSystemVmDao.update(profile.getId(), cleverCloudsSystemVm);
        return true;
    }

    @Override
    public void finalizeExpunge(VirtualMachine vm) {
        CleverCloudsSystemVm clevercloudSystemVm = cleverCloudSystemVmDao.findById(vm.getId());
        if (clevercloudSystemVm == null) {
            return;
        }
        clevercloudSystemVm.setPublicIpAddress(null);
        clevercloudSystemVm.setPrivateMacAddress(null);
        clevercloudSystemVm.setPrivateIpAddress(null);
        clevercloudSystemVm.setManagementIpAddress(null);
        cleverCloudSystemVmDao.update(clevercloudSystemVm.getId(), clevercloudSystemVm);
    }

    /**
     * For now we do not do anything here.
     */
    @Override
    public boolean finalizeStart(VirtualMachineProfile arg0, long arg1, Commands arg2, ReservationContext arg3) {
        return true;
    }

    /**
     * For now we do not do anything here.
     */
    @Override
    public void finalizeStop(VirtualMachineProfile arg0, Answer arg1) {
    }

    @Override
    public boolean finalizeVirtualMachineProfile(VirtualMachineProfile profile, DeployDestination dest, ReservationContext context) {
        StringBuilder buf = profile.getBootArgsBuilder();
        buf.append(" template=domP");
        buf.append(" host=").append(ApiServiceConfiguration.ManagementHostIPAdr.value());
        buf.append(" name=").append(profile.getVirtualMachine().getHostName());
        buf.append(" zone=").append(dest.getDataCenter().getId());
        buf.append(" pod=").append(dest.getPod().getId());
        buf.append(" guid=cleverCloudSystemVm.").append(profile.getId());
        buf.append(" cleverCloudSystemVm_vm=").append(profile.getId());

        boolean externalDhcp = false;
        String externalDhcpStr = configurationDao.getValue("direct.attach.network.externalIpAllocator.enabled");
        if (externalDhcpStr != null && externalDhcpStr.equalsIgnoreCase("true")) {
            externalDhcp = true;
        }
        if (Boolean.valueOf(configurationDao.getValue("system.vm.random.password"))) {
            buf.append(" vmpassword=").append(configurationDao.getValue("system.vm.password"));
        }

        for (NicProfile nic : profile.getNics()) {
            int deviceId = nic.getDeviceId();
            if (nic.getIPv4Address() == null) {
                buf.append(" eth").append(deviceId).append("ip=").append("0.0.0.0");
                buf.append(" eth").append(deviceId).append("mask=").append("0.0.0.0");
            } else {
                buf.append(" eth").append(deviceId).append("ip=").append(nic.getIPv4Address());
                buf.append(" eth").append(deviceId).append("mask=").append(nic.getIPv4Netmask());
            }

            if (nic.isDefaultNic()) {
                buf.append(" gateway=").append(nic.getIPv4Gateway());
            }

            if (nic.getTrafficType() == TrafficType.Management) {
                String mgmt_cidr = configurationDao.getValue(Config.ManagementNetwork.key());
                if (NetUtils.isValidCIDR(mgmt_cidr)) {
                    buf.append(" mgmtcidr=").append(mgmt_cidr);
                }
                buf.append(" localgw=").append(dest.getPod().getGateway());
            }
        }

        if (externalDhcp) {
            buf.append(" bootproto=dhcp");
        }
        DataCenterVO dc = datacenterDao.findById(profile.getVirtualMachine().getDataCenterId());
        buf.append(" internaldns1=").append(dc.getInternalDns1());
        if (dc.getInternalDns2() != null) {
            buf.append(" internaldns2=").append(dc.getInternalDns2());
        }
        buf.append(" dns1=").append(dc.getDns1());
        if (dc.getDns2() != null) {
            buf.append(" dns2=").append(dc.getDns2());
        }
        String bootArgs = buf.toString();
        logger.debug("Boot Args for " + profile + ": " + bootArgs);
        return true;
    }

    /**
     * For now we do not use it.
     */
    @Override
    public void prepareStop(VirtualMachineProfile arg0) {
    }

}
