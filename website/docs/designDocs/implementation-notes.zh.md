---
title: Implementation Notes
---

<!--
   Licensed to the Apache Software Foundation (ASF) under one or more
   contributor license agreements.  See the NOTICE file distributed with
   this work for additional information regarding copyright ownership.
   The ASF licenses this file to You under the Apache License, Version 2.0
   (the "License"); you may not use this file except in compliance with
   the License.  You may obtain a copy of the License at
   http://www.apache.org/licenses/LICENSE-2.0
   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
-->
[English](./implementation-notes.md) | 中文

在更深入的了解implementations之前，你應該先閱讀[architectue and requirements](./architecture-and-requirements.md)來了解整體需求及架構

以下是Submarine implementations的子目錄
* [Submarine Storage](./storage-implementation.md):  Submarine 如何儲存metadata、紀錄、metrics等
* [Submarine Environment](./environments-implementation.md): environment在submarine 中是如何被創建、管理及儲存
* [Submarine Experiment](./experiment-implementation.md): experiments是怎麼被管理、儲存，以及預定義的experiment是如何運行的
* [Submarine Notebook](./notebook-implementation.md): 如何管理notebook instance並使用Submarine SDK執行experiment
* [Submarine Server](./submarine-server/architecture.md): submarine server 的設計理念、架構、implementation notes 等

以下是還在設計中的項目，設計及審查完成後會將其移至上半部分
* [Submarine HA Design](./wip-designs/submarine-clusterServer.md): submarine HA如何被達成，使用RAFT等
* [Submarine services deployment module:](./wip-designs/submarine-launcher.md) 如何在k8s、yarn、雲端上部署submarine
