---
title: Architecture and Requirment
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

## Terminology

| Term | Description |
| -------- | -------- |
| User | 單一資料工程師/資料科學家. User has resource quota, credentials |
| Team | User 屬於一個或多個 teams, teams have ACLs for artifacts sharing such as notebook content, model, etc. |
| Admin | 也稱作 SRE, 可管理 user 的資源配額、credentials、所屬team、及其他components |


## 背景

如今大家都在談論機器學習，很多公司都在嘗試利用機器學習來推動業務的發展。隨著越來越多的開發者、軟體架構公司接觸這個領域，機器學習變得越來越容易實現。

在過去的十年裡，軟體行業為了解決以下不足之處，打造了很多機器學習的開源工具。

1. 人工構建邏輯回歸、GBDT等機器學習演算法並不容易。
**解決方法：** 業界已經開源了很多算法庫、工具，甚至是預訓練的模型，這樣資料科學家就可以直接用這些元件來hook up他們的數據，而不需要知道這些演算法和模型裡面複雜的細節。

2. 要想實現IDE中的"所見即所得（WYSIWYG）"並不容易，困難點在於將輸出、視覺化、故障診斷等整合在同一個地方。
**解決方法：** Notebooks的概念被加入到這個畫面中，notebook將交互式編碼、共享、可視化、調試的體驗帶到了同一個用戶界面下。有流行的開源筆記本，如Apache Zeppelin/Jupyter。

3. 管理依賴關係並不容易。 ML應用可以在一台機器上運行，但很難在另一台機器上部署，因為它有很多函式庫的依賴關係。
**解決方法：** 容器化開始流行，並成為打包依賴的標準，讓 "一次構建，隨處運行 "變得更容易。

4. 零碎的工具、函式庫對ML工程師來說很難學習。在一家公司學到的經驗無法自然運用到另一家公司。
**解決方法：** 幾個主流的開源框架，減少了學習太多不同框架、概念的開銷。數據科學家可以學習Tensorflow/PyTorch等幾個庫，再學習Keras等幾個高級封裝器，就能從其他開源構件中創建你的機器學習應用。

5. 同樣，由一個函式庫（如libsvm）建立的模型也很難被整合到機器學習管道中，因為沒有標準格式。
**解決方法：** 業界已經建立了成功的開源標準機器學習框架，如Tensorflow/PyTorch/Keras，所以它們的格式可以很容易地跨領域共享。而努力構建更通用的模型格式，如ONNX。

6. 很難建立一個數據管道，將數據從原始數據源流轉/轉換到ML應用所需的任何數據。
**解決方法：** 開源大數據行業在提供、簡化、統一數據流轉、轉換等流程和構件方面發揮了重要作用。

機器學習行業正在朝著解決重大路障的正確方向發展。那麼對於有機器學習需求的企業來說，現在的缺點是什麼？我們能在這裡幫到什麼？要回答這個問題，我們先來看看機器學習的工作流程。

## 機器學習工作流程及不足之處

```
1) From different data sources such as edge, clickstream, logs, etc.
   => Land to data lakes

2) From data lake, data transformation:
   => 資料轉換: 清理及移除無效的欄位, 抽樣，分割訓練資料及測試資料, 匯入表格等。
   => 為訓練資要做準備

3) From prepared data:
   => 訓練，模型超參數調整，交叉驗證等。
   => 模型儲存進資料庫.

4) From saved models:
   => 模型部署及A/B測試等
   => 部署用於在線服務或離線評分的模型。
```

一般來說資料科學家負責第2-4項，第一項則由其他的團隊處裡（多數公司稱之為資料工程師，有些資料工程師團隊也負責資料轉換的部份）

### Pain \#1 Complex workflow/steps from raw data to model, different tools needed by different steps, hard to make changes to workflow, and not error-proof

從原始數據到可用模型是一個複雜的工作流程，在與許多不同的資料科學家交流後，我們了解到訓練一個新模型並推動到生產的典型程序可能需要到1-2年。

這個工作流程所需要的技能也很廣泛。例如，大規模的資料轉換需要Spark / Hive這樣的工具，小規模則需要Pandas此類的工具。而模型訓練需要在XGBoost，Tensorflow，Keras，PyTorch之間切換。 建立資料工作流（Data Pipeline）需要Apache Airflow或Oozie。

是的，有許多出色的標準化開源工具可用於此類目的。但是如何對資料工作流的特定部分進行更改呢？如何在訓練數據中增加幾列以進行實驗？在投入生產之前，如何訓練模型、測試並驗證？這些步驟都需要在不同的工具、UI之間進行切換，很難進行更改，且無法確保這些過程沒有錯誤。

### Pain \#2 Dependencies of underlying resource management platform

To make jobs/services required by a machine learning platform to be able to run, we need an underlying resource management platform. There're some choices of resource management platform, and they have distinct advantages and disadvantages.

For example, there're many machine learning platform built on top of K8s. It is relatively easy to get a K8s from a cloud vendor, easy to orchestrate machine learning required services/daemons run on K8s. However, K8s doesn't offer good support jobs like Spark/Flink/Hive. So if your company has Spark/Flink/Hive running on YARN, there're gaps and a significant amount of work to move required jobs from YARN to K8s. Maintaining a separate K8s cluster is also overhead to Hadoop-based data infrastructure.

Similarly, if your company's data pipelines are mostly built on top of cloud resources and SaaS offerings, asking you to install a separate YARN cluster to run a new machine learning platform doesn't make a lot of sense.

### Pain \#3 Data scientist are forced to interact with lower-level platform components

In addition to the above pain, we do see Data Scientists are forced to learn underlying platform knowledge to be able to build a real-world machine learning workflow.

For most of the data scientists we talked with, they're experts of ML algorithms/libraries, feature engineering, etc. They're also most familiar with Python, R, and some of them understand Spark, Hive, etc.

If they're asked to do interactions with lower-level components like fine-tuning a Spark job's performance; or troubleshooting job failed to launch because of resource constraints; or write a K8s/YARN job spec and mount volumes, set networks properly. They will scratch their heads and typically cannot perform these operations efficiently.

### Pain \#4 Comply with data security/governance requirements

TODO: Add more details.

### Pain \#5 No good way to reduce routine ML code development

After the data is prepared, the data scientist needs to do several routine tasks to build the ML pipeline. To get a sense of the existing the data set, it usually needs a split of the data set, the statistics of data set. These tasks have a common duplicate part of code, which reduces the efficiency of data scientists.

An abstraction layer/framework to help the developer to boost ML pipeline development could be valuable. It's better than the developer only needs to fill callback function to focus on their key logic.

# Submarine

## Overview

### A little bit history

Initially, Submarine is built to solve problems of running deep learning jobs like Tensorflow/PyTorch on Apache Hadoop YARN, allows admin to monitor launched deep learning jobs, and manage generated models.

It was part of YARN initially, and code resides under `hadoop-yarn-applications`. Later, the community decided to convert it to be a subproject within Hadoop (Sibling project of YARN, HDFS, etc.) because we want to support other resource management platforms like K8s. And finally, we're reconsidering Submarine's charter, and the Hadoop community voted that it is the time to moved Submarine to a separate Apache TLP.

### Why Submarine?

`ONE PLATFORM`

Submarine is the ONE PLATFORM to allow Data Scientists to create end-to-end machine learning workflow. `ONE PLATFORM` means it supports Data Scientists and data engineers to finish their jobs on the same platform without frequently switching their toolsets. From dataset exploring data pipeline creation, model training, and tuning, and push model to production. All these steps can be completed within the `ONE PLATFORM`.

`Resource Management Independent`

It is also designed to be resource management independent, no matter if you have Apache Hadoop YARN, K8s, or just a container service, you will be able to run Submarine on top it.


## Requirements and non-requirements

### Notebook

1) Users should be able to create, edit, delete a notebook. (P0)
2) Notebooks can be persisted to storage and can be recovered if failure happens. (P0)
3) Users can trace back to history versions of a notebook. (P1)
4) Notebooks can be shared with different users. (P1)
5) Users can define a list of parameters of a notebook (looks like parameters of the notebook's main function) to allow executing a notebook like a job. (P1)
6) Different users can collaborate on the same notebook at the same time. (P2)

A running notebook instance is called notebook session (or session for short).

### Experiment

Experiments of Submarine is an offline task. It could be a shell command, a Python command, a Spark job, a SQL query, or even a workflow.

The primary purposes of experiments under Submarine's context is to do training tasks, offline scoring, etc. However, experiment can be generalized to do other tasks as well.

Major requirement of experiment:

1) Experiments can be submitted from UI/CLI/SDK.
2) Experiments can be monitored/managed from UI/CLI/SDK.
3) Experiments should not bind to one resource management platform (K8s/YARN).

#### Type of experiments

![](../assets/design/experiments.png)

There're two types of experiments:
`Adhoc experiments`: which includes a Python/R/notebook, or even an adhoc Tensorflow/PyTorch task, etc.

`Predefined experiment library`: This is specialized experiments, which including developed libraries such as CTR, BERT, etc. Users are only required to specify a few parameters such as input, output, hyper parameters, etc. Instead of worrying about where's training script/dependencies located.

#### Adhoc experiment

Requirements:

- Allow run adhoc scripts.
- Allow model engineer, data scientist to run Tensorflow/Pytorch programs on YARN/K8s/Container-cloud.
- Allow jobs easy access data/models in HDFS/s3, etc.
- Support run distributed Tensorflow/Pytorch jobs with simple configs.
- Support run user-specified Docker images.
- Support specify GPU and other resources.

#### Predefined experiment library

Here's an example of predefined experiment library to train deepfm model:

```
{
  "input": {
    "train_data": ["hdfs:///user/submarine/data/tr.libsvm"],
    "valid_data": ["hdfs:///user/submarine/data/va.libsvm"],
    "test_data": ["hdfs:///user/submarine/data/te.libsvm"],
    "type": "libsvm"
  },
  "output": {
    "save_model_dir": "hdfs:///user/submarine/deepfm",
    "metric": "auc"
  },
  "training": {
    "batch_size" : 512,
    "field_size": 39,
    "num_epochs": 3,
    "feature_size": 117581,
    ...
  }
}
```

Predefined experiment libraries can be shared across users on the same platform, users can also add new or modified predefined experiment library via UI/REST API.

We will also model AutoML, auto hyper-parameter tuning to predefined experiment library.

#### Pipeline

Pipeline is a special kind of experiment:

- A pipeline is a DAG of experiments.
- Can be also treated as a special kind of experiment.
- Users can submit/terminate a pipeline.
- Pipeline can be created/submitted via UI/API.

### Environment Profiles

Environment profiles (or environment for short) defines a set of libraries and when Docker is being used, a Docker image in order to run an experiment or a notebook.

Docker or VM image (such as AMI: Amazon Machine Images) defines the base layer of the environment.

On top of that, users can define a set of libraries (such as Python/R) to install.

Users can save different environment configs which can be also shared across the platform. Environment profiles can be used to run a notebook (e.g. by choosing different kernel from Jupyter), or an experiment. Predefined experiment library includes what environment to use so users don't have to choose which environment to use.

Environments can be added/listed/deleted/selected through CLI/SDK.

### Model

#### Model management

- Model artifacts are generated by experiments or notebook.
- A model consists of artifacts from one or multiple files.
- Users can choose to save, tag, version a produced model.
- Once The Model is saved, Users can do the online model serving or offline scoring of the model.

#### Model serving

After model saved, users can specify a serving script, a model and create a web service to serve the model.

We call the web service to "endpoint". Users can manage (add/stop) model serving endpoints via CLI/API/UI.

### Metrics for training job and model

Submarine-SDK provides tracking/metrics APIs, which allows developers to add tracking/metrics and view tracking/metrics from Submarine Workbench UI.

### Deployment

Submarine Services (See architecture overview below) should be deployed easily on-prem / on-cloud. Since there're more and more public cloud offering for compute/storage management on cloud, we need to support deploy Submarine compute-related workloads (such as notebook session, experiments, etc.) to cloud-managed clusters.

This also include Submarine may need to take input parameters from customers and create/manage clusters if needed. It is also a common requirement to use hybrid of on-prem/on-cloud clusters.

### Security / Access Control / User Management / Quota Management

There're 4 kinds of objects need access-control:

- Assets belong to Submarine system, which includes notebook, experiments and results, models, predefined experiment libraries, environment profiles.
- Data security. (Who owns what data, and what data can be accessed by each users).
- User credentials. (Such as LDAP).
- Other security, such as Git repo access, etc.

For the data security / user credentials / other security, it will be delegated to 3rd libraries such as Apache Ranger, IAM roles, etc.

Assets belong to Submarine system will be handled by Submarine itself.

Here're operations which Submarine admin can do for users / teams which can be used to access Submarine's assets.

**Operations for admins**

- Admin uses "User Management System" to onboard new users, upload user credentials, assign resource quotas, etc.
- Admins can create new users, new teams, update user/team mappings. Or remove users/teams.
- Admin can set resource quotas (if different from system default), permissions, upload/update necessary credentials (like Kerberos keytab) of a user.
- A DE/DS can also be an admin if the DE/DS has admin access. (Like a privileged user). This will be useful when a cluster is exclusively shared by a user or only shared by a small team.
- `Resource Quota Management System` helps admin to manage resources quotas of teams, organizations. Resources can be machine resources like CPU/Memory/Disk, etc. It can also include non-machine resources like $$-based budgets.

### Dataset

There's also need to tag dataset which will be used for training and shared across the platform by different users.

Like mentioned above, access to the actual data will be handled by 3rd party system like Apache Ranger / Hive Metastore which is out of the Submarine's scope.

## Architecture Overview

### Architecture Diagram

```
     +-----------------------------------------------------------------+
     |            Submarine UI / CLI / REST API / SDK                  |
     |                 Mini-Submarine                                  |
     +-----------------------------------------------------------------+

     +--------------------Submarine Server-----------------------------+
     | +---------+ +---------+ +----------+ +----------+ +------------+|
     | |Data set | |Notebooks| |Experiment| |Models    | |Servings    ||
     | +---------+ +---------+ +----------+ +----------+ +------------+|
     |-----------------------------------------------------------------|
     |                                                                 |
     | +-----------------+ +-----------------+ +---------------------+ |
     | |Experiment       | |Compute Resource | |Other Management     | |
     | |Manager          | |   Manager       | |Services             | |
     | +-----------------+ +-----------------+ +---------------------+ |
     |   Spark, template      YARN/K8s/Docker                          |
     |   TF, PyTorch, pipeline                                         |
     |                                                                 |
     + +-----------------+                                             +
     | |Submarine Meta   |                                             |
     | |    Store        |                                             |
     | +-----------------+                                             |
     |                                                                 |
     +-----------------------------------------------------------------+

      (You can use http://stable.ascii-flow.appspot.com/#Draw
      to draw such diagrams)
```

`Compute Resource Manager` Helps to manage compute resources on-prem/on-cloud, this module can also handle cluster creation / management, etc.

`Experiment Manager` Work with "Compute Resource Manager" to submit different kinds of workloads such as (distributed) Tensorflow / Pytorch, etc.

`Submarine SDK` provides Java/Python/REST API to allow DS or other engineers to integrate into Submarine services. It also includes a `mini-submarine` component that launches Submarine components from a single Docker container (or a VM image).

Details of Submarine Server design can be found at [submarine-server-design](./submarine-server/architecture.md).

