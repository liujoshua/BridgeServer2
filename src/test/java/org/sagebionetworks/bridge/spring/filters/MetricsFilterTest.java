package org.sagebionetworks.bridge.spring.filters;

import static org.apache.http.HttpHeaders.USER_AGENT;
import static org.sagebionetworks.bridge.BridgeConstants.X_FORWARDED_FOR_HEADER;
import static org.sagebionetworks.bridge.BridgeConstants.X_REQUEST_ID_HEADER;
import static org.sagebionetworks.bridge.TestConstants.TIMESTAMP;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import javax.servlet.FilterChain;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.fasterxml.jackson.databind.JsonNode;

import org.joda.time.DateTimeUtils;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.cache.CacheKey;
import org.sagebionetworks.bridge.cache.CacheProvider;
import org.sagebionetworks.bridge.models.Metrics;

public class MetricsFilterTest extends Mockito {

    @Mock
    private CacheProvider mockCacheProvider;
    
    @Mock
    private HttpServletRequest mockRequest;
    
    @Mock
    private HttpServletResponse mockResponse;
    
    @Mock
    private FilterChain mockFilterChain;
    
    @Captor
    ArgumentCaptor<CacheKey> cacheKeyCaptor;
    
    @Captor
    ArgumentCaptor<Metrics> metricsCaptor;
    
    @InjectMocks
    private MetricsFilter filter = new MetricsFilter();

    @BeforeMethod
    private void before() {
        DateTimeUtils.setCurrentMillisFixed(TIMESTAMP.getMillis());
        MockitoAnnotations.initMocks(this);
    }
    
    @AfterMethod
    private void after() {
        DateTimeUtils.setCurrentMillisSystem();
    }

    @Test
    public void metricsTest() throws Exception {
        when(mockRequest.getHeader(X_REQUEST_ID_HEADER)).thenReturn("request-id");
        when(mockRequest.getMethod()).thenReturn("GET");
        when(mockRequest.getServletPath()).thenReturn("/v3/api");
        when(mockRequest.getProtocol()).thenReturn("1");
        when(mockRequest.getRemoteAddr()).thenReturn(TestConstants.IP_ADDRESS);
        when(mockRequest.getHeader(USER_AGENT)).thenReturn("userAgent/1");
        when(mockResponse.getStatus()).thenReturn(201);
        
        filter.doFilter(mockRequest, mockResponse, mockFilterChain);
        
        verify(mockCacheProvider).setObject(cacheKeyCaptor.capture(), metricsCaptor.capture(),
                eq(BridgeConstants.METRICS_EXPIRE_SECONDS));
        
        CacheKey key = cacheKeyCaptor.getValue();
        assertEquals("request-id:Metrics:Metrics", key.toString());
        
        Metrics metrics = metricsCaptor.getValue();
        JsonNode node = metrics.getJson();
        
        assertEquals("request-id", node.get("request_id").textValue());
        assertEquals("GET", node.get("method").textValue());
        assertEquals("/v3/api", node.get("uri").textValue());
        assertEquals("1", node.get("protocol").textValue());
        assertEquals(1, node.get("version").intValue());
        assertEquals(TestConstants.IP_ADDRESS, node.get("remote_address").textValue());
        assertEquals("userAgent/1", node.get("user_agent").textValue());
        assertEquals(TIMESTAMP.toString(), node.get("start").textValue());
        assertEquals(TIMESTAMP.toString(), node.get("end").textValue());
        assertTrue(node.get("elapsedMillis").intValue() > -1);
        assertEquals(201, node.get("status").intValue());
        
        verify(mockFilterChain).doFilter(mockRequest, mockResponse);
        verify(mockCacheProvider).removeObject(any());
    }
    
    @Test
    public void metricsUsesXForwardedForHeader() throws Exception {
        when(mockRequest.getHeader(X_REQUEST_ID_HEADER)).thenReturn("request-id");
        when(mockRequest.getHeader(X_FORWARDED_FOR_HEADER)).thenReturn("5.6.7.8");
        when(mockRequest.getRemoteAddr()).thenReturn(TestConstants.IP_ADDRESS);
        
        filter.doFilter(mockRequest, mockResponse, mockFilterChain);
        
        verify(mockCacheProvider).setObject(any(), metricsCaptor.capture(),
                eq(BridgeConstants.METRICS_EXPIRE_SECONDS));
        
        Metrics metrics = metricsCaptor.getValue();
        JsonNode node = metrics.getJson();
        
        assertEquals("5.6.7.8", node.get("remote_address").textValue());
    }
}
