/*
 * This program is part of Autonomiccs "autonomic-platform",
 * an open source autonomic cloud computing management platform.
 * Copyright (C) 2016 Autonomiccs, Inc.
 *
 * Licensed to the Autonomiccs, Inc. under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The Autonomiccs, Inc. licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package br.com.autonomiccs.autonomic.plugin.common.services;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.cloud.dc.ClusterVO;
import com.cloud.dc.dao.ClusterDao;

/**
 * This class is intended to execute clusters operations; that means, searching, a single clusters,
 * a set of clusters, updating data in the database and others. To interact with the database this
 * class will use DAOs objects to interact with tables such as the "cluster" table.
 */
@Service
public class ClusterService {

    @Autowired
    private ClusterDao clusterDao;

    /**
     * It returns all clusters in the Pod with the given id.
     */
    public List<ClusterVO> listAllClustersFromPod(long podId) {
        return clusterDao.listByPodId(podId);
    }

    /**
     * It returns the {@link ClusterVO} with the given id.
     */
    public ClusterVO findById(long clusterId) {
        return clusterDao.findById(clusterId);
    }

    /**
     * It returns all clusters in the Zone with the given id.
     */
    public List<ClusterVO> listAllClustersOnZone(Long zoneId) {
        return clusterDao.listClustersByDcId(zoneId);
    }

    /**
     * It returns all clusters in the cloud.
     */
    public List<ClusterVO> listAllClusters() {
        return clusterDao.listAll();
    }

}
