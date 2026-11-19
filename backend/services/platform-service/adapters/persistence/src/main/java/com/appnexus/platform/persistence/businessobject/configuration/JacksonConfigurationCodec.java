package com.appnexus.platform.persistence.businessobject.configuration;

import com.appnexus.platform.businessobject.port.ConfigurationCodec;
import com.appnexus.contracts.businessobject.BusinessObjects.Binding;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;

/** 原配置 JSON 协议的 Jackson 适配器；保持既有格式与异常码，不提供无效快照回退。 */
public final class JacksonConfigurationCodec implements ConfigurationCodec {
  private final ObjectMapper json = new ObjectMapper();

  /** {@inheritDoc} */
  @Override public List<Binding> bindings(Object snapshot) {
    return read(snapshot, new TypeReference<>() {});
  }

  /** {@inheritDoc} */
  @Override public List<String> operations(Object snapshot) {
    return read(snapshot, new TypeReference<>() {});
  }

  /** {@inheritDoc} */
  @Override public Map<String, String> facts(Object snapshot) {
    return read(snapshot, new TypeReference<>() {});
  }

  /** {@inheritDoc} */
  @Override public String encode(Object snapshot) {
    try { return json.writeValueAsString(snapshot); }
    catch (Exception error) { throw new IllegalArgumentException("BUSINESS_OBJECT_INPUT_INVALID", error); }
  }

  private <T> T read(Object snapshot, TypeReference<T> type) {
    try { return json.readValue(snapshot.toString(), type); }
    catch (Exception error) { throw new IllegalStateException("BUSINESS_OBJECT_STORED_CONTRACT_INVALID", error); }
  }
}
