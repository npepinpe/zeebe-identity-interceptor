package org.camunda.community.zeebe.interceptors.identity;

import dasniko.testcontainers.keycloak.KeycloakContainer;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.HttpWaitStrategy;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
public final class InterceptorIT {
  private static final Network network = Network.newNetwork();

  @Container
  private static final KeycloakContainer KEYCLOAK =
      new KeycloakContainer()
          .withAdminUsername("admin")
          .withAdminPassword("admin")
          .withNetwork(network)
          .withNetworkAliases("keycloak");

  @Container
  private static final GenericContainer<?> IDENTITY =
      new GenericContainer<>(DockerImageName.parse("camunda/identity").withTag("8.1.5"))
          .dependsOn(KEYCLOAK)
          .withEnv(
              "IDENTITY_AUTH_PROVIDER_BACKEND_URL",
              "http://keycloak:8080/auth/realms/camunda-platform")
          .withEnv(
              "IDENTITY_AUTH_PROVIDER_ISSUER_URL",
              "http://keycloak:8080/auth/realms/camunda-platform")
          .withEnv("KEYCLOAK_SETUP_USER", "admin")
          .withEnv("KEYCLOAK_SETUP_PASSWORD", "admin")
          .withEnv("KEYCLOAK_URL", "http://keycloak:8080/auth")
          .withNetwork(network)
          .withExposedPorts(8080, 8082)
          .waitingFor(
              new HttpWaitStrategy()
                  .forPort(8082)
                  .forPath("/actuator/health")
                  .allowInsecure()
                  .forStatusCode(200))
          .withNetworkAliases("identity");

  @Test
  void shouldInterceptCalls() {
    // given

    // when

    // then
    Assertions.assertThat(true).isTrue();
  }
}
