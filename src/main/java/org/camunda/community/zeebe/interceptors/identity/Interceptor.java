package org.camunda.community.zeebe.interceptors.identity;

import io.camunda.identity.sdk.Identity;
import io.camunda.identity.sdk.authentication.AccessToken;
import io.camunda.identity.sdk.authentication.exception.*;
import io.camunda.zeebe.util.Environment;
import io.grpc.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public final class Interceptor implements ServerInterceptor {
  private static final Logger LOGGER = LoggerFactory.getLogger(Interceptor.class);
  private static final Metadata.Key<String> AUTH_KEY =
      Metadata.Key.of("Authorization", Metadata.ASCII_STRING_MARSHALLER);

  private final Config config;
  private final Identity identity;

  public Interceptor() {
    this(readConfig());
  }

  Interceptor(final Config config) {
    this(config, createIdentity(config));
  }

  Interceptor(final Config config, final Identity identity) {
    this.config = config;
    this.identity = identity;
  }

  private static Config readConfig() {
    final var factory = new ConfigFactory();
    return factory.create(new Environment(), "zeebe.gateway.interceptors.identity", Config.class);
  }

  private static Identity createIdentity(final Config config) {
    return new Identity(config.identity());
  }

  @Override
  public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
      ServerCall<ReqT, RespT> call, Metadata headers, ServerCallHandler<ReqT, RespT> next) {
    final var methodDescriptor = call.getMethodDescriptor();
    final var token = headers.get(AUTH_KEY);
    if (token == null) {
      LOGGER.debug(
          "Denying call {} as no token was provided", methodDescriptor.getFullMethodName());
      return deny(
          call,
          Status.UNAUTHENTICATED.augmentDescription(
              "Expected bearer token at header with key [%s], but found nothing"
                  .formatted(AUTH_KEY.name())));
    }

    final AccessToken accessToken;
    try {
      accessToken = identity.authentication().verifyToken(token.replaceFirst("^Bearer ", ""));
    } catch (final TokenDecodeException
        | InvalidSignatureException
        | TokenExpiredException
        | InvalidClaimException
        | JsonWebKeyException e) {
      if (LOGGER.isTraceEnabled()) {
        LOGGER.trace(
            "Denying call {} as the token [{}] could not be fully verified",
            methodDescriptor.getFullMethodName(),
            token);
      } else {
        LOGGER.debug(
            "Denying call {} as the token could not be fully verified",
            methodDescriptor.getFullMethodName());
      }

      return deny(
          call,
          Status.PERMISSION_DENIED
              .augmentDescription("Failed to parse bearer token, see cause for details")
              .withCause(e));
    }

    final var permissions = computePermissionSet(methodDescriptor);
    if (Collections.disjoint(permissions, accessToken.getPermissions())) {
      LOGGER.debug(
          "Denying call {} for {}; expected permissions {} but got {}",
          methodDescriptor.getFullMethodName(),
          accessToken.getUserDetails(),
          permissions,
          accessToken.getPermissions());

      return deny(
          call,
          Status.PERMISSION_DENIED.augmentDescription(
              "Missing one of the following permissions: %s".formatted(permissions)));
    }

    return next.startCall(call, headers);
  }

  private Set<String> computePermissionSet(final MethodDescriptor<?, ?> method) {
    final Set<String> permissions = new HashSet<>();
    final var serviceName = method.getServiceName();

    if (serviceName != null) {
      permissions.add(serviceName);
    }

    permissions.add(method.getFullMethodName());
    return permissions;
  }

  private <ReqT> ServerCall.Listener<ReqT> deny(
      final ServerCall<ReqT, ?> call, final Status status) {
    call.close(status, new Metadata());
    return new ServerCall.Listener<>() {};
  }
}
