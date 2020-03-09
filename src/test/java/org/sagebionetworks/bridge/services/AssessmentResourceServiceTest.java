package org.sagebionetworks.bridge.services;

import static org.sagebionetworks.bridge.BridgeConstants.SHARED_STUDY_ID_STRING;
import static org.sagebionetworks.bridge.RequestContext.NULL_INSTANCE;
import static org.sagebionetworks.bridge.TestConstants.APP_ID;
import static org.sagebionetworks.bridge.TestConstants.ASSESSMENT_ID;
import static org.sagebionetworks.bridge.TestConstants.CREATED_ON;
import static org.sagebionetworks.bridge.TestConstants.GUID;
import static org.sagebionetworks.bridge.TestConstants.MODIFIED_ON;
import static org.sagebionetworks.bridge.TestConstants.OWNER_ID;
import static org.sagebionetworks.bridge.TestConstants.RESOURCE_CATEGORIES;
import static org.sagebionetworks.bridge.TestConstants.SHARED_STUDY_IDENTIFIER;
import static org.sagebionetworks.bridge.TestConstants.USER_SUBSTUDY_IDS;
import static org.sagebionetworks.bridge.models.ResourceList.CATEGORIES;
import static org.sagebionetworks.bridge.models.ResourceList.INCLUDE_DELETED;
import static org.sagebionetworks.bridge.models.ResourceList.MAX_REVISION;
import static org.sagebionetworks.bridge.models.ResourceList.MIN_REVISION;
import static org.sagebionetworks.bridge.models.ResourceList.OFFSET_BY;
import static org.sagebionetworks.bridge.models.ResourceList.PAGE_SIZE;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;

import java.util.Optional;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.RequestContext;
import org.sagebionetworks.bridge.dao.AssessmentResourceDao;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.exceptions.UnauthorizedException;
import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.assessments.Assessment;
import org.sagebionetworks.bridge.models.assessments.AssessmentResource;
import org.sagebionetworks.bridge.models.assessments.AssessmentResourceTest;
import org.sagebionetworks.bridge.models.assessments.AssessmentTest;

public class AssessmentResourceServiceTest extends Mockito {
    private static final String UNSANITIZED_STRING = "bad string<script>removeme</script>";
    private static final String SANITIZED_STRING = "bad string";
    
    @Mock
    AssessmentResourceDao mockDao;
    
    @Mock
    AssessmentService mockAssessmentService;
    
    @Captor
    ArgumentCaptor<AssessmentResource> resourceCaptor;
    
    @InjectMocks
    @Spy
    AssessmentResourceService service;
    
    @BeforeMethod
    public void beforeMethod() {
        MockitoAnnotations.initMocks(this);
        
        when(service.getCreatedOn()).thenReturn(CREATED_ON);
        when(service.getModifiedOn()).thenReturn(MODIFIED_ON);
        when(service.generateGuid()).thenReturn(GUID);
    }
    
    @AfterMethod
    public void afterMethod() {
        BridgeUtils.setRequestContext(NULL_INSTANCE);
    }
    
    @Test
    public void getResources() {
        Assessment assessment = AssessmentTest.createAssessment();
        when(mockAssessmentService.getLatestAssessment(APP_ID, ASSESSMENT_ID)).thenReturn(assessment);
        
        AssessmentResource resource = AssessmentResourceTest.createAssessmentResource();
        resource.setCreatedAtRevision(5); // assessment revision = 5
        when(mockDao.getResources(APP_ID, ASSESSMENT_ID, 10, 40, RESOURCE_CATEGORIES, 1, 100, true))
            .thenReturn(new PagedResourceList<>(ImmutableList.of(resource), 10));
        
        PagedResourceList<AssessmentResource> retValue = service.getResources(
                APP_ID, ASSESSMENT_ID, 10, 40, RESOURCE_CATEGORIES, 1, 100, true);
        assertEquals(retValue.getItems().size(), 1);
        assertTrue(retValue.getItems().get(0).isUpToDate());
        assertSame(retValue.getItems().get(0), resource);
        assertEquals(retValue.getRequestParams().get(OFFSET_BY), 10);
        assertEquals(retValue.getRequestParams().get(PAGE_SIZE), 40);
        assertEquals(retValue.getRequestParams().get(CATEGORIES), RESOURCE_CATEGORIES);
        assertEquals(retValue.getRequestParams().get(MIN_REVISION), 1);
        assertEquals(retValue.getRequestParams().get(MAX_REVISION), 100);
        assertTrue((Boolean)retValue.getRequestParams().get(INCLUDE_DELETED));
        
        verify(mockAssessmentService).getLatestAssessment(APP_ID, ASSESSMENT_ID);
        verify(mockDao).getResources(APP_ID, ASSESSMENT_ID, 10, 40, RESOURCE_CATEGORIES, 1, 100, true);
    }
    
    @Test
    public void getResourcesNullArguments() {
        Assessment assessment = AssessmentTest.createAssessment();
        when(mockAssessmentService.getLatestAssessment(APP_ID, ASSESSMENT_ID)).thenReturn(assessment);
        
        AssessmentResource resource = AssessmentResourceTest.createAssessmentResource();
        resource.setCreatedAtRevision(4); // assessment revision = 5
        when(mockDao.getResources(APP_ID, ASSESSMENT_ID, null, null, null, null, null, false))
            .thenReturn(new PagedResourceList<>(ImmutableList.of(resource), 10));
        
        PagedResourceList<AssessmentResource> retValue = service.getResources(
                APP_ID, ASSESSMENT_ID, null, null, null, null, null, false);
        assertEquals(retValue.getItems().size(), 1);
        assertFalse(retValue.getItems().get(0).isUpToDate());
        assertSame(retValue.getItems().get(0), resource);
        
        verify(mockAssessmentService).getLatestAssessment(APP_ID, ASSESSMENT_ID);
        verify(mockDao).getResources(APP_ID, ASSESSMENT_ID, null, null, null, null, null, false);
    }
    
    @Test(expectedExceptions = BadRequestException.class,
            expectedExceptionsMessageRegExp = "maxRevision cannot be greater than minRevision")
    public void getResourcesMaxHigherThanMinRevision() {
        service.getResources(APP_ID, ASSESSMENT_ID, null, null, null, 3, 2, false);
    }

    @Test
    public void getResource() {
        Assessment assessment = AssessmentTest.createAssessment();
        when(mockAssessmentService.getLatestAssessment(APP_ID, ASSESSMENT_ID)).thenReturn(assessment);
        
        AssessmentResource resource = AssessmentResourceTest.createAssessmentResource();
        resource.setCreatedAtRevision(5); // assessment revision = 5
        when(mockDao.getResource(GUID)).thenReturn(Optional.of(resource));
        
        AssessmentResource retValue = service.getResource(APP_ID, ASSESSMENT_ID, GUID);
        assertTrue(retValue.isUpToDate());
    }

    @Test(expectedExceptions = EntityNotFoundException.class,
            expectedExceptionsMessageRegExp = "AssessmentResource not found.")
    public void getResourceNotFound() {
        Assessment assessment = AssessmentTest.createAssessment();
        when(mockAssessmentService.getLatestAssessment(APP_ID, ASSESSMENT_ID)).thenReturn(assessment);
        
        when(mockDao.getResource(GUID)).thenReturn(Optional.empty());
        
        service.getResource(APP_ID, ASSESSMENT_ID, GUID);
    }
    
    @Test
    public void createResource() {
        Assessment assessment = AssessmentTest.createAssessment();
        when(mockAssessmentService.getLatestAssessment(APP_ID, ASSESSMENT_ID)).thenReturn(assessment);
        
        AssessmentResource resource = AssessmentResourceTest.createAssessmentResource();
        resource.setDeleted(true);
        when(mockDao.saveResource(eq(APP_ID), eq(ASSESSMENT_ID), any())).thenReturn(resource);
        
        AssessmentResource retValue = service.createResource(APP_ID, ASSESSMENT_ID, resource);
        assertSame(retValue, resource);
        assertEquals(resource.getGuid(), GUID);
        assertEquals(resource.getCreatedOn(), CREATED_ON);
        assertEquals(resource.getModifiedOn(), CREATED_ON);
        assertFalse(resource.isDeleted());
        assertEquals(resource.getCreatedAtRevision(), 5);
        
        verify(mockDao).saveResource(APP_ID, ASSESSMENT_ID, resource);
    }
    
    @Test(expectedExceptions = UnauthorizedException.class)
    public void createResourceChecksAssessmentOwnership() {
        BridgeUtils.setRequestContext(new RequestContext.Builder()
                .withCallerSubstudies(ImmutableSet.of("substudy2")).build());
        
        Assessment assessment = AssessmentTest.createAssessment();
        when(mockAssessmentService.getLatestAssessment(APP_ID, ASSESSMENT_ID)).thenReturn(assessment);
        
        service.createResource(APP_ID, ASSESSMENT_ID, new AssessmentResource());
    }

    @Test(expectedExceptions = InvalidEntityException.class)
    public void createResourceValidates() {
        Assessment assessment = AssessmentTest.createAssessment();
        when(mockAssessmentService.getLatestAssessment(APP_ID, ASSESSMENT_ID)).thenReturn(assessment);
        
        service.createResource(APP_ID, ASSESSMENT_ID, new AssessmentResource());
    }
    
    @Test
    public void createResourceSanitizesStringFields() {
        Assessment assessment = AssessmentTest.createAssessment();
        when(mockAssessmentService.getLatestAssessment(APP_ID, ASSESSMENT_ID)).thenReturn(assessment);
        
        AssessmentResource resource = createUnsanitizedResource();
        when(mockDao.saveResource(eq(APP_ID), eq(ASSESSMENT_ID), any())).thenReturn(resource);
        
        AssessmentResource retValue = service.createResource(APP_ID, ASSESSMENT_ID, resource);
        assertEquals(retValue.getGuid(), GUID);
        assertEquals(retValue.getTitle(), SANITIZED_STRING);
        assertEquals(retValue.getUrl(), SANITIZED_STRING);
        assertEquals(retValue.getFormat(), SANITIZED_STRING);
        assertEquals(retValue.getDate(), SANITIZED_STRING);
        assertEquals(retValue.getDescription(), SANITIZED_STRING);
        assertEquals(retValue.getContributors().get(0), SANITIZED_STRING);
        assertEquals(retValue.getCreators().get(0), SANITIZED_STRING);
        assertEquals(retValue.getPublishers().get(0), SANITIZED_STRING);
        assertEquals(retValue.getLanguage(), SANITIZED_STRING);
    }
    
    @Test
    public void updateResource() {
        Assessment assessment = AssessmentTest.createAssessment();
        when(mockAssessmentService.getLatestAssessment(APP_ID, ASSESSMENT_ID)).thenReturn(assessment);
        
        AssessmentResource existing = AssessmentResourceTest.createAssessmentResource();
        existing.setModifiedOn(null);
        existing.setDeleted(false);
        when(mockDao.getResource(GUID)).thenReturn(Optional.of(existing));
        
        AssessmentResource resource = AssessmentResourceTest.createAssessmentResource();
        resource.setModifiedOn(null);
        when(mockDao.saveResource(eq(APP_ID), eq(ASSESSMENT_ID), any())).thenReturn(resource);
        
        AssessmentResource retValue = service.updateResource(APP_ID, ASSESSMENT_ID, resource);
        assertSame(retValue, resource);
        assertEquals(retValue.getModifiedOn(), MODIFIED_ON);
        assertEquals(retValue.getCreatedAtRevision(), 5);
        assertTrue(retValue.isUpToDate());
    }
    
    @Test
    public void updateResourceSanitizesStringFields() {
        Assessment assessment = AssessmentTest.createAssessment();
        when(mockAssessmentService.getLatestAssessment(APP_ID, ASSESSMENT_ID)).thenReturn(assessment);
        
        AssessmentResource existing = AssessmentResourceTest.createAssessmentResource();
        existing.setDeleted(false);
        when(mockDao.getResource(GUID)).thenReturn(Optional.of(existing));
        
        AssessmentResource resource = createUnsanitizedResource();
        resource.setGuid(GUID); // this actually can't be changed, or you get a 404
        when(mockDao.saveResource(eq(APP_ID), eq(ASSESSMENT_ID), any())).thenReturn(resource);
        
        AssessmentResource retValue = service.updateResource(APP_ID, ASSESSMENT_ID, resource);
        assertEquals(retValue.getGuid(), GUID);
        assertEquals(retValue.getTitle(), SANITIZED_STRING);
        assertEquals(retValue.getUrl(), SANITIZED_STRING);
        assertEquals(retValue.getFormat(), SANITIZED_STRING);
        assertEquals(retValue.getDate(), SANITIZED_STRING);
        assertEquals(retValue.getDescription(), SANITIZED_STRING);
        assertEquals(retValue.getContributors().get(0), SANITIZED_STRING);
        assertEquals(retValue.getCreators().get(0), SANITIZED_STRING);
        assertEquals(retValue.getPublishers().get(0), SANITIZED_STRING);
        assertEquals(retValue.getLanguage(), SANITIZED_STRING);
    }
    
    @Test(expectedExceptions = InvalidEntityException.class)
    public void updateResourceValidates() {
        Assessment assessment = AssessmentTest.createAssessment();
        when(mockAssessmentService.getLatestAssessment(APP_ID, ASSESSMENT_ID)).thenReturn(assessment);
        
        AssessmentResource existing = AssessmentResourceTest.createAssessmentResource();
        existing.setDeleted(false);
        when(mockDao.getResource(GUID)).thenReturn(Optional.of(existing));
        
        AssessmentResource resource = new AssessmentResource();
        resource.setGuid(GUID); // this actually can't be changed, or you get a 404
        when(mockDao.saveResource(eq(APP_ID), eq(ASSESSMENT_ID), any())).thenReturn(resource);
        
        service.updateResource(APP_ID, ASSESSMENT_ID, resource);
    }
    
    @Test(expectedExceptions = UnauthorizedException.class)
    public void updateResourceChecksAssessmentOwnership() {
        BridgeUtils.setRequestContext(new RequestContext.Builder()
                .withCallerSubstudies(ImmutableSet.of("substudy2")).build());

        Assessment assessment = AssessmentTest.createAssessment();
        when(mockAssessmentService.getLatestAssessment(APP_ID, ASSESSMENT_ID)).thenReturn(assessment);
        
        AssessmentResource resource = AssessmentResourceTest.createAssessmentResource();
        
        service.updateResource(APP_ID, ASSESSMENT_ID, resource);
    }
    
    @Test(expectedExceptions = EntityNotFoundException.class,
          expectedExceptionsMessageRegExp = "AssessmentResource not found.")
    public void updateResourceNotFound() {
        Assessment assessment = AssessmentTest.createAssessment();
        when(mockAssessmentService.getLatestAssessment(APP_ID, ASSESSMENT_ID)).thenReturn(assessment);
        
        when(mockDao.getResource(GUID)).thenReturn(Optional.empty());
        
        AssessmentResource resource = AssessmentResourceTest.createAssessmentResource();
        resource.setModifiedOn(null);
        when(mockDao.saveResource(eq(APP_ID), eq(ASSESSMENT_ID), any())).thenReturn(resource);
        
        service.updateResource(APP_ID, ASSESSMENT_ID, resource);
    }
    
    @Test(expectedExceptions = EntityNotFoundException.class,
            expectedExceptionsMessageRegExp = "AssessmentResource not found.")
    public void updateResourceNotFoundWhenLogicallyDeleted() {
        Assessment assessment = AssessmentTest.createAssessment();
        when(mockAssessmentService.getLatestAssessment(APP_ID, ASSESSMENT_ID)).thenReturn(assessment);
        
        AssessmentResource existing = AssessmentResourceTest.createAssessmentResource();
        existing.setDeleted(true);
        when(mockDao.getResource(GUID)).thenReturn(Optional.of(existing));
        
        AssessmentResource resource = AssessmentResourceTest.createAssessmentResource();
        resource.setDeleted(true);
        
        service.updateResource(APP_ID, ASSESSMENT_ID, resource);
    }
    
    @Test
    public void updateSharedResource() { 
        Assessment assessment = AssessmentTest.createAssessment();
        assessment.setOwnerId(APP_ID + ":" + OWNER_ID);
        when(mockAssessmentService.getLatestAssessment(SHARED_STUDY_IDENTIFIER, ASSESSMENT_ID)).thenReturn(assessment);
        
        AssessmentResource existing = AssessmentResourceTest.createAssessmentResource();
        existing.setModifiedOn(null);
        existing.setDeleted(false);
        when(mockDao.getResource(GUID)).thenReturn(Optional.of(existing));
        
        AssessmentResource resource = AssessmentResourceTest.createAssessmentResource();
        resource.setModifiedOn(null);
        when(mockDao.saveResource(eq(SHARED_STUDY_IDENTIFIER), eq(ASSESSMENT_ID), any())).thenReturn(resource);
        
        AssessmentResource retValue = service.updateSharedResource(APP_ID, ASSESSMENT_ID, resource);
        assertSame(retValue, resource);
        assertEquals(retValue.getModifiedOn(), MODIFIED_ON);
        assertEquals(retValue.getCreatedAtRevision(), 5);
        assertTrue(retValue.isUpToDate());
    }
    
    @Test(expectedExceptions = UnauthorizedException.class)
    public void updateSharedResourceFails() {
        BridgeUtils.setRequestContext(new RequestContext.Builder().withCallerSubstudies(USER_SUBSTUDY_IDS).build());
        
        Assessment assessment = AssessmentTest.createAssessment();
        assessment.setOwnerId(APP_ID + ":anotherOrg");
        when(mockAssessmentService.getLatestAssessment(SHARED_STUDY_ID_STRING, ASSESSMENT_ID)).thenReturn(assessment);

        AssessmentResource resource = AssessmentResourceTest.createAssessmentResource();
        
        service.updateSharedResource(APP_ID, ASSESSMENT_ID, resource);
    }
    
    @Test
    public void deleteResource() {
        Assessment assessment = AssessmentTest.createAssessment();
        when(mockAssessmentService.getLatestAssessment(APP_ID, ASSESSMENT_ID)).thenReturn(assessment);

        AssessmentResource existing = AssessmentResourceTest.createAssessmentResource();
        existing.setModifiedOn(null);
        existing.setDeleted(false);
        when(mockDao.getResource(GUID)).thenReturn(Optional.of(existing));
        
        service.deleteResource(APP_ID, ASSESSMENT_ID, GUID);
        
        verify(mockDao).saveResource(eq(APP_ID), eq(ASSESSMENT_ID), resourceCaptor.capture());
        
        AssessmentResource captured = resourceCaptor.getValue();
        assertTrue(captured.isDeleted());
        assertEquals(captured.getModifiedOn(), MODIFIED_ON);
    }
    
    @Test(expectedExceptions = UnauthorizedException.class)
    public void deleteResourceChecksAssessmentOwnership() {
        BridgeUtils.setRequestContext(new RequestContext.Builder()
                .withCallerSubstudies(ImmutableSet.of("substudy2")).build());        

        Assessment assessment = AssessmentTest.createAssessment();
        when(mockAssessmentService.getLatestAssessment(APP_ID, ASSESSMENT_ID)).thenReturn(assessment);

        service.deleteResource(APP_ID, ASSESSMENT_ID, GUID);
    }
    
    
    @Test(expectedExceptions = EntityNotFoundException.class,
            expectedExceptionsMessageRegExp = "AssessmentResource not found.")
    public void deleteResourceNotFound() { 
        Assessment assessment = AssessmentTest.createAssessment();
        when(mockAssessmentService.getLatestAssessment(APP_ID, ASSESSMENT_ID)).thenReturn(assessment);

        when(mockDao.getResource(GUID)).thenReturn(Optional.empty());
        
        service.deleteResource(APP_ID, ASSESSMENT_ID, GUID);
    }

    @Test
    public void deleteResourcePermanently() {
        AssessmentResource existing = AssessmentResourceTest.createAssessmentResource();
        when(mockDao.getResource(GUID)).thenReturn(Optional.of(existing));
        
        service.deleteResourcePermanently(APP_ID, ASSESSMENT_ID, GUID);
        
        verify(mockDao).deleteResource(existing);
    }
    
    @Test
    public void deleteResourcePermanentlyQuietWhenNotFound() {
        when(mockDao.getResource(GUID)).thenReturn(Optional.empty());
        
        service.deleteResourcePermanently(APP_ID, ASSESSMENT_ID, GUID);
        
        verify(mockDao, never()).deleteResource(any());
    }
    
    private AssessmentResource createUnsanitizedResource() {
        AssessmentResource resource = AssessmentResourceTest.createAssessmentResource();
        resource.setGuid(UNSANITIZED_STRING);
        resource.setTitle(UNSANITIZED_STRING);
        resource.setUrl(UNSANITIZED_STRING);
        resource.setFormat(UNSANITIZED_STRING);
        resource.setDate(UNSANITIZED_STRING);
        resource.setDescription(UNSANITIZED_STRING);
        resource.setContributors(ImmutableList.of(UNSANITIZED_STRING));
        resource.setCreators(ImmutableList.of(UNSANITIZED_STRING));
        resource.setPublishers(ImmutableList.of(UNSANITIZED_STRING));
        resource.setLanguage(UNSANITIZED_STRING);
        return resource;
    }
}
