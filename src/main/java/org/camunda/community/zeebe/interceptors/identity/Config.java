package org.camunda.community.zeebe.interceptors.identity;

import io.camunda.identity.sdk.IdentityConfiguration;

public record Config(IdentityConfiguration identity) {}
