/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.submarine.rest;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.kubernetes.client.ApiClient;
import io.kubernetes.client.ApiException;
import io.kubernetes.client.Configuration;
import io.kubernetes.client.JSON;
import io.kubernetes.client.apis.CustomObjectsApi;
import io.kubernetes.client.util.ClientBuilder;
import io.kubernetes.client.util.KubeConfig;
import org.apache.commons.httpclient.methods.DeleteMethod;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.submarine.server.AbstractSubmarineServerTest;
import org.apache.submarine.server.api.environment.Environment;
import org.apache.submarine.server.api.experiment.Experiment;
import org.apache.submarine.server.api.notebook.Notebook;
import org.apache.submarine.server.api.notebook.NotebookId;
import org.apache.submarine.server.gson.NotebookIdDeserializer;
import org.apache.submarine.server.gson.NotebookIdSerializer;
import org.apache.submarine.server.response.JsonResponse;
import org.apache.submarine.server.rest.RestConstants;
import org.joda.time.DateTime;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.FileReader;
import java.io.IOException;
import java.util.Date;

@SuppressWarnings("rawtypes")
public class NotebookRestApiIT extends AbstractSubmarineServerTest {

  private static final Logger LOG = LoggerFactory.getLogger(NotebookRestApiIT.class);

  private static CustomObjectsApi k8sApi;
  private static final String BASE_API_PATH = "/api/" + RestConstants.V1 + "/" + RestConstants.NOTEBOOK;
  public static final String VERSION = "v1";
  public static final String GROUP = "kubeflow.org";
  public static final String PLURAL = "notebooks";

  private final Gson gson = new GsonBuilder()
          .registerTypeAdapter(NotebookId.class, new NotebookIdSerializer())
          .registerTypeAdapter(NotebookId.class, new NotebookIdDeserializer())
          .create();

  @BeforeClass
  public static void startUp() throws IOException {
    Assert.assertTrue(checkIfServerIsRunning());

    // The kube config path defined by kind-cluster-build.sh
    String confPath = System.getProperty("user.home") + "/.kube/kind-config-kind";
    KubeConfig config = KubeConfig.loadKubeConfig(new FileReader(confPath));
    ApiClient client = ClientBuilder.kubeconfig(config).build();
    Configuration.setDefaultApiClient(client);
    k8sApi = new CustomObjectsApi();
  }

  @Test
  public void testServerPing() throws IOException {
    GetMethod response = httpGet(BASE_API_PATH + "/" + RestConstants.PING);
    String requestBody = response.getResponseBodyAsString();
    Gson gson = new Gson();
    JsonResponse jsonResponse = gson.fromJson(requestBody, JsonResponse.class);
    Assert.assertEquals(Response.Status.OK.getStatusCode(), jsonResponse.getCode());
    Assert.assertEquals("Pong", jsonResponse.getResult().toString());
  }

  @Test
  public void testCreateNotebookWithJsonSpec() throws Exception {
    // create environment
    String envBody = loadContent("environment/test_env_3.json");
    run(envBody, "application/json");

    Gson gson = new GsonBuilder().create();
    GetMethod getMethod = httpGet(ENV_PATH + "/" + ENV_NAME);
    Assert.assertEquals(Response.Status.OK.getStatusCode(),
            getMethod.getStatusCode());

    String json = getMethod.getResponseBodyAsString();
    JsonResponse jsonResponse = gson.fromJson(json, JsonResponse.class);
    Assert.assertEquals(Response.Status.OK.getStatusCode(),
            jsonResponse.getCode());

    Environment getEnvironment =
            gson.fromJson(gson.toJson(jsonResponse.getResult()), Environment.class);
    Assert.assertEquals(ENV_NAME, getEnvironment.getEnvironmentSpec().getName());

    String body = loadContent("notebook/notebook-req.json");
    runTest(body, "application/json");

    deleteEnvironment();
  }

  @Test
  public void testCreateNotebookWithYamlSpec() throws Exception {
    // create environment
    String envBody = loadContent("environment/test_env_3.json");
    run(envBody, "application/json");

    Gson gson = new GsonBuilder().create();
    GetMethod getMethod = httpGet(ENV_PATH + "/" + ENV_NAME);
    Assert.assertEquals(Response.Status.OK.getStatusCode(),
            getMethod.getStatusCode());

    String json = getMethod.getResponseBodyAsString();
    JsonResponse jsonResponse = gson.fromJson(json, JsonResponse.class);
    Assert.assertEquals(Response.Status.OK.getStatusCode(),
            jsonResponse.getCode());

    Environment getEnvironment =
            gson.fromJson(gson.toJson(jsonResponse.getResult()), Environment.class);
    Assert.assertEquals(ENV_NAME, getEnvironment.getEnvironmentSpec().getName());

    String body = loadContent("notebook/notebook-req.yaml");
    runTest(body, "application/yaml");

    deleteEnvironment();
  }

  @Test
  public void testCreateNotebookWithInvalidSpec() throws Exception {
    PostMethod postMethod = httpPost(BASE_API_PATH, "", MediaType.APPLICATION_JSON);
    Assert.assertEquals(Response.Status.OK.getStatusCode(), postMethod.getStatusCode());

    String json = postMethod.getResponseBodyAsString();
    JsonResponse jsonResponse = gson.fromJson(json, JsonResponse.class);
    Assert.assertEquals(Response.Status.OK.getStatusCode(), jsonResponse.getCode());
  }

  private void runTest(String body, String contentType) throws Exception {
    // create
    LOG.info("Create a notebook server by Notebook REST API");
    PostMethod postMethod = httpPost(BASE_API_PATH, body, contentType);
    Assert.assertEquals(Response.Status.OK.getStatusCode(), postMethod.getStatusCode());

    String json = postMethod.getResponseBodyAsString();
    JsonResponse jsonResponse = gson.fromJson(json, JsonResponse.class);
    Assert.assertEquals(Response.Status.OK.getStatusCode(), jsonResponse.getCode());

    Notebook createdNotebook = gson.fromJson(gson.toJson(jsonResponse.getResult()), Notebook.class);
    verifyCreateNotebookApiResult(createdNotebook);

    // find
    GetMethod getMethod = httpGet(BASE_API_PATH + "/" + createdNotebook.getNotebookId().toString());
    Assert.assertEquals(Response.Status.OK.getStatusCode(), getMethod.getStatusCode());

    json = getMethod.getResponseBodyAsString();
    jsonResponse = gson.fromJson(json, JsonResponse.class);
    Assert.assertEquals(Response.Status.OK.getStatusCode(), jsonResponse.getCode());

    Notebook foundNotebook = gson.fromJson(gson.toJson(jsonResponse.getResult()), Notebook.class);
    verifyGetNotebookApiResult(createdNotebook, foundNotebook);

    // delete
    DeleteMethod deleteMethod =
            httpDelete(BASE_API_PATH + "/" + createdNotebook.getNotebookId().toString());
    Assert.assertEquals(Response.Status.OK.getStatusCode(), deleteMethod.getStatusCode());

    json = deleteMethod.getResponseBodyAsString();
    jsonResponse = gson.fromJson(json, JsonResponse.class);
    Assert.assertEquals(Response.Status.OK.getStatusCode(), jsonResponse.getCode());

    Notebook deletedNotebook = gson.fromJson(gson.toJson(jsonResponse.getResult()), Notebook.class);
    verifyDeleteNotebookApiResult(createdNotebook, deletedNotebook);
  }

  private void verifyCreateNotebookApiResult(Notebook createdNotebook) {
    Assert.assertNotNull(createdNotebook.getUid());
    Assert.assertNotNull(createdNotebook.getCreatedTime());
    Assert.assertEquals(Experiment.Status.STATUS_CREATED.getValue(), createdNotebook.getStatus());
  }

  private void verifyGetNotebookApiResult(Notebook createdNotebook,
                                          Notebook foundNotebook) throws Exception {
    Assert.assertEquals(createdNotebook.getNotebookId(), foundNotebook.getNotebookId());
    Assert.assertEquals(createdNotebook.getUid(), foundNotebook.getUid());
    Assert.assertEquals(createdNotebook.getCreatedTime(), foundNotebook.getCreatedTime());
    Assert.assertEquals(createdNotebook.getName(), foundNotebook.getName());
    assertGetK8sResult(foundNotebook);
  }

  private void verifyDeleteNotebookApiResult(Notebook createdNotebook,
                                             Notebook deletedNotebook) {
    Assert.assertEquals(createdNotebook.getName(), deletedNotebook.getName());
    Assert.assertEquals(Notebook.Status.STATUS_DELETED.getValue(), deletedNotebook.getStatus());
    assertDeleteK8sResult(deletedNotebook);
  }

  private void assertGetK8sResult(Notebook notebook) throws Exception {
    JsonObject rootObject = getNotebookByK8sApi(GROUP, VERSION, notebook.getSpec().getMeta().getNamespace(),
            PLURAL, notebook.getName());

    JsonObject metadataObject = rootObject.getAsJsonObject("metadata");
    String uid = metadataObject.getAsJsonPrimitive("uid").getAsString();
    LOG.info("Uid from Notebook REST is {}", notebook.getUid());
    LOG.info("Uid from K8s REST is {}", uid);
    Assert.assertEquals(notebook.getUid(), uid);

    JsonArray envVars = (JsonArray) rootObject.getAsJsonObject("spec")
            .getAsJsonObject("template").getAsJsonObject("spec")
            .getAsJsonArray("containers").get(0).getAsJsonObject().get("env");
    Assert.assertNotNull("The environment command not found.", envVars);

    String creationTimestamp =
            metadataObject.getAsJsonPrimitive("creationTimestamp").getAsString();
    Date expectedDate = new DateTime(notebook.getCreatedTime()).toDate();
    Date actualDate = new DateTime(creationTimestamp).toDate();
    LOG.info("CreationTimestamp from Notebook REST is {}", expectedDate);
    LOG.info("CreationTimestamp from K8s REST is {}", actualDate);
    Assert.assertEquals(expectedDate, actualDate);
  }

  private void assertDeleteK8sResult(Notebook notebook) {
    JsonObject rootObject = null;
    try {
      rootObject = getNotebookByK8sApi(GROUP, VERSION, notebook.getSpec().getMeta().getNamespace(),
              PLURAL, notebook.getName());
    } catch (ApiException e) {
      Assert.assertEquals(Response.Status.NOT_FOUND.getStatusCode(), e.getCode());
    } finally {
      Assert.assertNull(rootObject);
    }
  }

  private JsonObject getNotebookByK8sApi(String group, String version, String namespace, String plural,
                                   String name) throws ApiException {
    Object obj = k8sApi.getNamespacedCustomObject(group, version, namespace, plural, name);
    Gson gson = new JSON().getGson();
    JsonObject rootObject = gson.toJsonTree(obj).getAsJsonObject();
    Assert.assertNotNull("Parse the K8s API Server response failed.", rootObject);
    return rootObject;
  }
}
