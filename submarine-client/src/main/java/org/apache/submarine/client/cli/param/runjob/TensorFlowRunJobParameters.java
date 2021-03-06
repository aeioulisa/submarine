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

package org.apache.submarine.client.cli.param.runjob;

import com.google.common.collect.Lists;
import org.apache.commons.cli.ParseException;
import org.apache.commons.lang3.StringUtils;
import org.apache.hadoop.yarn.api.records.Resource;
import org.apache.hadoop.yarn.exceptions.YarnException;
import org.apache.submarine.client.cli.CliConstants;
import org.apache.submarine.client.cli.CliUtils;
import org.apache.submarine.client.cli.runjob.RoleParameters;
import org.apache.submarine.commons.runtime.ClientContext;
import org.apache.submarine.commons.runtime.param.Parameter;
import org.apache.submarine.commons.runtime.api.TensorFlowRole;
import org.apache.submarine.commons.runtime.resource.ResourceUtils;

import java.io.IOException;
import java.util.List;

/**
 * Parameters for TensorFlow job.
 */
public class TensorFlowRunJobParameters extends RunJobParameters {
  private boolean tensorboardEnabled;
  private static final String CANNOT_BE_DEFINED_FOR_TF =
      "cannot be defined for TensorFlow jobs!";
  private RoleParameters psParameters =
      RoleParameters.createEmpty(TensorFlowRole.PS);
  private RoleParameters tensorBoardParameters =
      RoleParameters.createEmpty(TensorFlowRole.TENSORBOARD);

  @Override
  public void updateParameters(Parameter parametersHolder, ClientContext clientContext)
      throws ParseException, IOException, YarnException {
    checkArguments(parametersHolder);
    super.updateParameters(parametersHolder, clientContext);

    String input = parametersHolder.getOptionValue(CliConstants.INPUT_PATH);
    this.workerParameters =
        generateWorkerParameters(clientContext, parametersHolder, input);
    this.psParameters = getPSParameters(clientContext, parametersHolder);
    this.distributed = determineIfDistributed(workerParameters.getReplicas(),
        psParameters.getReplicas());

    if (parametersHolder.hasOption(CliConstants.TENSORBOARD)) {
      this.tensorboardEnabled = true;
      this.tensorBoardParameters =
          getTensorBoardParameters(parametersHolder, clientContext);
    }
    executePostOperations(clientContext);
  }

  @Override
  void executePostOperations(ClientContext clientContext) throws IOException {
    // Set default job dir / saved model dir, etc.
    setDefaultDirs(clientContext);
    replacePatternsInParameters(clientContext);
  }

  private void checkArguments(Parameter parametersHolder)
      throws YarnException, ParseException {
    if (parametersHolder.getOptionValue(CliConstants.N_SCHEDULERS) != null) {
      throw new ParseException(getParamCannotBeDefinedErrorMessage(
          CliConstants.N_SCHEDULERS));
    } else if (parametersHolder.getOptionValue(CliConstants.SCHEDULER_RES) != null) {
      throw new ParseException(getParamCannotBeDefinedErrorMessage(
          CliConstants.SCHEDULER_RES));
    } else if (parametersHolder
        .getOptionValue(CliConstants.SCHEDULER_DOCKER_IMAGE) != null) {
      throw new ParseException(getParamCannotBeDefinedErrorMessage(
          CliConstants.SCHEDULER_DOCKER_IMAGE));
    } else if (parametersHolder
        .getOptionValue(CliConstants.SCHEDULER_LAUNCH_CMD) != null) {
      throw new ParseException(getParamCannotBeDefinedErrorMessage(
          CliConstants.SCHEDULER_LAUNCH_CMD));
    }
  }

  private String getParamCannotBeDefinedErrorMessage(String cliName) {
    return String.format(
        "Parameter '%s' " + CANNOT_BE_DEFINED_FOR_TF, cliName);
  }

  private void replacePatternsInParameters(ClientContext clientContext)
      throws IOException {
    if (StringUtils.isNotEmpty(getPSLaunchCmd())) {
      String afterReplace = CliUtils.replacePatternsInLaunchCommand(
          getPSLaunchCmd(), this, clientContext.getRemoteDirectoryManager());
      setPSLaunchCmd(afterReplace);
    }

    if (StringUtils.isNotEmpty(getWorkerLaunchCmd())) {
      String afterReplace =
          CliUtils.replacePatternsInLaunchCommand(getWorkerLaunchCmd(), this,
              clientContext.getRemoteDirectoryManager());
      setWorkerLaunchCmd(afterReplace);
    }
  }

  @Override
  public List<String> getLaunchCommands() {
    return Lists.newArrayList(getWorkerLaunchCmd(), getPSLaunchCmd());
  }

  private boolean determineIfDistributed(int nWorkers, int nPS)
      throws ParseException {
    // Check #workers and #ps.
    // When distributed training is required
    if (nWorkers >= 2 && nPS > 0) {
      return true;
    } else if (nWorkers <= 1 && nPS > 0) {
      throw new ParseException("Only specified one worker but non-zero PS, "
          + "please double check.");
    }
    return false;
  }

  private RoleParameters getPSParameters(ClientContext clientContext,
      Parameter parametersHolder)
      throws YarnException, IOException, ParseException {
    int nPS = getNumberOfPS(parametersHolder);
    Resource psResource =
        determinePSResource(parametersHolder, nPS, clientContext);
    String psDockerImage =
        parametersHolder.getOptionValue(CliConstants.PS_DOCKER_IMAGE);
    String psLaunchCommand =
        parametersHolder.getOptionValue(CliConstants.PS_LAUNCH_CMD);
    return new RoleParameters(TensorFlowRole.PS, nPS, psLaunchCommand,
        psDockerImage, psResource);
  }

  private Resource determinePSResource(Parameter parametersHolder,
      int nPS, ClientContext clientContext)
      throws ParseException, YarnException, IOException {
    if (nPS > 0) {
      String psResourceStr =
          parametersHolder.getOptionValue(CliConstants.PS_RES);
      if (psResourceStr == null) {
        throw new ParseException("--" + CliConstants.PS_RES + " is absent.");
      }
      return ResourceUtils.createResourceFromString(psResourceStr);
    }
    return null;
  }

  private int getNumberOfPS(Parameter parametersHolder)
      throws YarnException {
    int nPS = 0;
    if (parametersHolder.getOptionValue(CliConstants.N_PS) != null) {
      nPS =
          Integer.parseInt(parametersHolder.getOptionValue(CliConstants.N_PS));
    }
    return nPS;
  }

  private RoleParameters getTensorBoardParameters(Parameter parametersHolder,
      ClientContext clientContext) throws YarnException, IOException {
    String tensorboardResourceStr =
        parametersHolder.getOptionValue(CliConstants.TENSORBOARD_RESOURCES);
    if (tensorboardResourceStr == null || tensorboardResourceStr.isEmpty()) {
      tensorboardResourceStr = CliConstants.TENSORBOARD_DEFAULT_RESOURCES;
    }
    Resource tensorboardResource = ResourceUtils.createResourceFromString(
            tensorboardResourceStr);
    String tensorboardDockerImage =
        parametersHolder.getOptionValue(CliConstants.TENSORBOARD_DOCKER_IMAGE);
    return new RoleParameters(TensorFlowRole.TENSORBOARD, 1, null,
        tensorboardDockerImage, tensorboardResource);
  }

  public RoleParameters getPsParameters() {
    return psParameters;
  }

  public void setPsParameters(RoleParameters parameters) {
    this.psParameters = parameters;
  }

  public int getNumPS() {
    return psParameters.getReplicas();
  }

  public void setNumPS(int numPS) {
    psParameters.setReplicas(numPS);
  }

  public Resource getPsResource() {
    return psParameters.getResource();
  }

  public void setPsResource(Resource resource) {
    psParameters.setResource(resource);
  }

  public String getPsDockerImage() {
    return psParameters.getDockerImage();
  }

  public void setPsDockerImage(String image) {
    psParameters.setDockerImage(image);
  }

  public String getPSLaunchCmd() {
    return psParameters.getLaunchCommand();
  }

  public void setPSLaunchCmd(String launchCmd) {
    psParameters.setLaunchCommand(launchCmd);
  }

  public RoleParameters getTensorBoardParameters() {
    return tensorBoardParameters;
  }

  public void setTensorBoardParameters(RoleParameters tensorBoardParameters) {
    this.tensorBoardParameters = tensorBoardParameters;
  }

  public boolean isTensorboardEnabled() {
    return tensorboardEnabled;
  }

  public void setTensorboardEnabled(boolean tensorboardEnabled) {
    this.tensorboardEnabled = tensorboardEnabled;
  }

  public Resource getTensorboardResource() {
    return tensorBoardParameters.getResource();
  }

  public void setTensorboardResource(Resource resource) {
    tensorBoardParameters.setResource(resource);
  }

  public String getTensorboardDockerImage() {
    return tensorBoardParameters.getDockerImage();
  }

  public void setTensorboardDockerImage(String image) {
    tensorBoardParameters.setDockerImage(image);
  }

}
