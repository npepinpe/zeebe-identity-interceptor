package org.camunda.community.zeebe.interceptors.identity;

import io.camunda.zeebe.util.Environment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.boot.context.properties.bind.BindResult;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySource;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertiesPropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

public final class ConfigFactory {
  private static final Logger LOG = LoggerFactory.getLogger(ConfigFactory.class);

  /**
   * Reads the configuration file from the class path and binds it to an object
   *
   * @param environment environment to simulate environment variables that can be overlaid; may be
   *     {@code} null
   * @param prefix the top level element in the configuration that should be mapped to the object
   * @param type type of object to be created; it is assumed that this object has a public no arg
   *     constructor; must not be {@code null}
   */
  public <T> T create(final Environment environment, final String prefix, final Class<T> type) {
    final var fileName = "config.yml";

    LOG.debug("Reading configuration for {} from file {}", type, fileName);
    try (InputStream inputStream = new ClassPathResource(fileName).getInputStream()) {
      return create(environment, prefix, inputStream, type);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /**
   * Reads the configuration from the input stream and binds it to an object
   *
   * @param environment environment to simulate environment variables that can be overlayed; may be
   *     {@code} null
   * @param prefix the top level element in the configuration that should be mapped to the object
   * @param inputStream input stream of the configuration file; must not be {@code null}
   * @param type type of object to be created; it is assumed that this object has a public no arg
   *     constructor; must not be {@code null}
   */
  public <T> T create(
      final Environment environment,
      final String prefix,
      final InputStream inputStream,
      final Class<T> type) {
    LOG.debug("Reading configuration for {} from input stream", type);

    final Map<String, Object> propertiesFromEnvironment = convertEnvironmentIntoMap(environment);
    final Properties propertiesFromFile = loadYamlProperties(inputStream);

    final MutablePropertySources propertySources = new MutablePropertySources();

    propertySources.addLast(
        new MapPropertySource("environment properties strict", propertiesFromEnvironment));
    propertySources.addLast(
        new PropertiesPropertySource("properties from file", propertiesFromFile));

    final Constructor<T> constructor;
    final T target;
    try {
      constructor = type.getConstructor();
      target = constructor.newInstance();
    } catch (NoSuchMethodException
        | InstantiationException
        | IllegalAccessException
        | InvocationTargetException e) {
      throw new IllegalStateException(e);
    }

    final Iterable<ConfigurationPropertySource> configPropertySource =
        ConfigurationPropertySources.from(propertySources);

    final BindResult<T> bindResult = new Binder(configPropertySource).bind(prefix, type);

    if (!bindResult.isBound()) {
      LOG.warn(
          "No binding result parsing the configuration. This is normal if the configuration is empty."
              + " Otherwise it is a configuration or programming error.");
      return target;
    } else {
      return bindResult.get();
    }
  }

  private Properties loadYamlProperties(final InputStream inputStream) {
    final Resource resource = new InputStreamResource(inputStream);
    final YamlPropertiesFactoryBean factoryBean = new YamlPropertiesFactoryBean();
    factoryBean.setResources(resource);
    return factoryBean.getObject();
  }

  private Map<String, Object> convertEnvironmentIntoMap(final Environment environment) {
    final Map<String, Object> result = new HashMap<>();

    if (environment != null) {
      final Set<String> propertyKeys = environment.getPropertyKeys();
      for (String propertyKey : propertyKeys) {
        result.put(propertyKey, environment.get(propertyKey).orElse(null));
      }
    }

    return result;
  }
}
