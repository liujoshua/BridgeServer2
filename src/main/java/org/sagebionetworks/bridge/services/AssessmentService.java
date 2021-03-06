package org.sagebionetworks.bridge.services;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.jsoup.safety.Whitelist.none;
import static org.jsoup.safety.Whitelist.simpleText;
import static org.sagebionetworks.bridge.AuthEvaluatorField.ORG_ID;
import static org.sagebionetworks.bridge.AuthEvaluatorField.OWNER_ID;
import static org.sagebionetworks.bridge.AuthUtils.IS_ORG_MEMBER;
import static org.sagebionetworks.bridge.AuthUtils.IS_ORG_MEMBER_IN_APP;
import static org.sagebionetworks.bridge.BridgeConstants.API_MAXIMUM_PAGE_SIZE;
import static org.sagebionetworks.bridge.BridgeConstants.API_MINIMUM_PAGE_SIZE;
import static org.sagebionetworks.bridge.BridgeConstants.NEGATIVE_OFFSET_ERROR;
import static org.sagebionetworks.bridge.BridgeConstants.NONPOSITIVE_REVISION_ERROR;
import static org.sagebionetworks.bridge.BridgeConstants.PAGE_SIZE_ERROR;
import static org.sagebionetworks.bridge.BridgeConstants.SHARED_APP_ID;
import static org.sagebionetworks.bridge.BridgeUtils.sanitizeHTML;
import static org.sagebionetworks.bridge.models.OperatingSystem.SYNONYMS;
import static org.sagebionetworks.bridge.models.ResourceList.GUID;
import static org.sagebionetworks.bridge.models.ResourceList.IDENTIFIER;
import static org.sagebionetworks.bridge.models.ResourceList.INCLUDE_DELETED;
import static org.sagebionetworks.bridge.models.ResourceList.OFFSET_BY;
import static org.sagebionetworks.bridge.models.ResourceList.PAGE_SIZE;
import static org.sagebionetworks.bridge.models.ResourceList.TAGS;
import static org.sagebionetworks.bridge.util.BridgeCollectors.toImmutableSet;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

import org.apache.commons.lang3.StringUtils;
import org.joda.time.DateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.RequestContext;
import org.sagebionetworks.bridge.dao.AssessmentDao;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.EntityAlreadyExistsException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.exceptions.UnauthorizedException;
import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.assessments.Assessment;
import org.sagebionetworks.bridge.models.assessments.config.AssessmentConfig;
import org.sagebionetworks.bridge.models.assessments.config.PropertyInfo;
import org.sagebionetworks.bridge.validators.AssessmentValidator;
import org.sagebionetworks.bridge.validators.Validate;

@Component
public class AssessmentService {
    static final String IDENTIFIER_REQUIRED = "identifier required";

    private AssessmentDao dao;
    
    private AssessmentConfigService configService;
    
    private OrganizationService organizationService;
    
    @Autowired
    final void setAssessmentDao(AssessmentDao assessmentDao) {
        this.dao = assessmentDao;
    }
    
    @Autowired
    final void setAssessmentConfigService(AssessmentConfigService configService) {
        this.configService = configService;
    }
    
    @Autowired
    final void setOrganizationService(OrganizationService organizationService) {
        this.organizationService = organizationService;
    }
    
    // accessor to mock for tests
    String generateGuid() {
        return BridgeUtils.generateGuid();
    }

    // accessor to mock for tests
    DateTime getCreatedOn() {
        return DateTime.now();
    }
    
    // accessor to mock for tests
    DateTime getModifiedOn() {
        return DateTime.now();
    }
    
    // accessor to mock for tests
    int getPageSize() {
        return API_MAXIMUM_PAGE_SIZE;
    }
    
    public PagedResourceList<Assessment> getAssessments(String appId, int offsetBy, int pageSize,
            Set<String> tags, boolean includeDeleted) {
        checkArgument(isNotBlank(appId));
        
        if (offsetBy < 0) {
            throw new BadRequestException(NEGATIVE_OFFSET_ERROR);
        }
        if (pageSize < API_MINIMUM_PAGE_SIZE || pageSize > API_MAXIMUM_PAGE_SIZE) {
            throw new BadRequestException(PAGE_SIZE_ERROR);
        }
        return dao.getAssessments(appId, offsetBy, pageSize, tags, includeDeleted)
                .withRequestParam(OFFSET_BY, offsetBy)
                .withRequestParam(PAGE_SIZE, pageSize)
                .withRequestParam(INCLUDE_DELETED, includeDeleted)
                .withRequestParam(TAGS, tags);
    }
    
    public Assessment createAssessment(String appId, Assessment assessment) {
        checkArgument(isNotBlank(appId));
        checkNotNull(assessment);
        
        // If the identifier is missing, it will be a validation error.
        if (assessment.getIdentifier() != null) {
            // If an assessment under this identifier exists, use the revisions API. We want to 
            // warn people when they are unintentionally stomping on an existing identifier.
            
            Optional<Assessment> opt = getLatestInternal(appId, assessment.getIdentifier(), true);
            if (opt.isPresent()) {
                Assessment e = opt.get();
                Map<String,Object> map = new ImmutableMap.Builder<String,Object>()
                        .put("identifier", e.getIdentifier())
                        .put("revision", e.getRevision()).build();
                throw new EntityAlreadyExistsException(Assessment.class, 
                        "Assessment with this identifier exists, revision=" + e.getRevision() + 
                        ", deleted="+e.isDeleted(), map);
            }
        }
        return createAssessmentInternal(appId, assessment);
    }
    
    public Assessment createAssessmentRevision(String appId, String guid, Assessment assessment) {
        checkArgument(isNotBlank(appId));
        checkNotNull(assessment);
        
        // Verify this is an existing assessment, and that we're trying to add a revision
        // with the same identifier.
        Assessment existing = getAssessmentByGuid(appId, guid);
        assessment.setIdentifier(existing.getIdentifier());

        return createAssessmentInternal(appId, assessment);
    }
        
    public Assessment updateAssessment(String appId, Assessment assessment) {
        checkArgument(isNotBlank(appId));
        checkNotNull(assessment);
        
        Assessment existing = dao.getAssessment(appId, assessment.getGuid())
                .orElseThrow(() -> new EntityNotFoundException(Assessment.class));
        
        if (existing.isDeleted() && assessment.isDeleted()) {
            throw new EntityNotFoundException(Assessment.class);
        }
        IS_ORG_MEMBER.checkAndThrow(ORG_ID, existing.getOwnerId());
        
        return updateAssessmentInternal(appId, assessment, existing);
    }
    
    public Assessment updateSharedAssessment(String callerAppId, Assessment assessment) {
        checkArgument(isNotBlank(callerAppId));
        checkNotNull(assessment);
        
        Assessment existing = dao.getAssessment(SHARED_APP_ID, assessment.getGuid())
                .orElseThrow(() -> new EntityNotFoundException(Assessment.class));
        if (existing.isDeleted() && assessment.isDeleted()) {
            throw new EntityNotFoundException(Assessment.class);
        }

        IS_ORG_MEMBER_IN_APP.checkAndThrow(OWNER_ID, existing.getOwnerId());
        
        return updateAssessmentInternal(SHARED_APP_ID, assessment, existing);
    }
    
    private Assessment updateAssessmentInternal(String appId, Assessment assessment, Assessment existing) {
        assessment.setIdentifier(existing.getIdentifier());
        assessment.setOwnerId(existing.getOwnerId());
        assessment.setOriginGuid(existing.getOriginGuid());
        assessment.setCreatedOn(existing.getCreatedOn());
        DateTime timestamp = getModifiedOn();
        assessment.setModifiedOn(timestamp);
        sanitizeAssessment(assessment);

        String osName = assessment.getOsName();
        if (SYNONYMS.get(osName) != null) {
            assessment.setOsName(SYNONYMS.get(osName));
        }
        AssessmentValidator validator = new AssessmentValidator(appId, organizationService);
        Validate.entityThrowingException(validator, assessment);

        return dao.updateAssessment(appId, assessment);        
    }
        
    public Assessment getAssessmentByGuid(String appId, String guid) {
        checkArgument(isNotBlank(appId));
        checkArgument(isNotBlank(guid));
        
        return dao.getAssessment(appId, guid)
                .orElseThrow(() -> new EntityNotFoundException(Assessment.class));
    }
        
    public Assessment getAssessmentById(String appId, String identifier, int revision) {
        checkArgument(isNotBlank(appId));
        checkArgument(isNotBlank(identifier));
        
        if (revision < 1) {
            throw new BadRequestException(NONPOSITIVE_REVISION_ERROR);
        }
        return dao.getAssessment(appId, identifier, revision)
                .orElseThrow(() -> new EntityNotFoundException(Assessment.class));
    }
        
    public Assessment getLatestAssessment(String appId, String identifier) {
        checkArgument(isNotBlank(appId));
        checkArgument(isNotBlank(identifier));
        
        return getLatestInternal(appId, identifier, false)
            .orElseThrow(() -> new EntityNotFoundException(Assessment.class));
    }
        
    public PagedResourceList<Assessment> getAssessmentRevisionsById(
        String appId, String identifier, int offsetBy, int pageSize, boolean includeDeleted) {
        checkArgument(isNotBlank(appId));
        
        if (isBlank(identifier)) {
            throw new BadRequestException(IDENTIFIER_REQUIRED);
        }
        if (offsetBy < 0) {
            throw new BadRequestException(NEGATIVE_OFFSET_ERROR);
        }
        if (pageSize < API_MINIMUM_PAGE_SIZE || pageSize > API_MAXIMUM_PAGE_SIZE) {
            throw new BadRequestException(PAGE_SIZE_ERROR);
        }
        PagedResourceList<Assessment> page = dao.getAssessmentRevisions(
                appId, identifier, offsetBy, pageSize, includeDeleted);
        // If there are no matches, this identifier is bogus.
        if (page.getTotal() == 0) {
            throw new EntityNotFoundException(Assessment.class);
        }
        return page.withRequestParam(IDENTIFIER, identifier)
                .withRequestParam(OFFSET_BY, offsetBy)
                .withRequestParam(PAGE_SIZE, pageSize)
                .withRequestParam(INCLUDE_DELETED, includeDeleted);
    }
    
    public PagedResourceList<Assessment> getAssessmentRevisionsByGuid(String appId, 
            String guid, int offsetBy, int pageSize, boolean includeDeleted) {
            checkArgument(isNotBlank(appId));
            
       Assessment assessment = getAssessmentByGuid(appId, guid);
       return getAssessmentRevisionsById(appId, assessment.getIdentifier(), 
               offsetBy, pageSize, includeDeleted).withRequestParam(GUID, guid);
    }

    /**
     * Takes a reference to an assessment in the caller's context (appId), and creates an 
     * assessment in the shared context. Stores a scoped organization identifier (appId + 
     * orgId) as the "ownerId" in the sshared context for authorization checks on 
     * operations in the shared context (only members of the owning organization in the 
     * origin app can edit their shared assessments). The caller must be associated to 
     * the organization that own's the assessment locally.
     */
    public Assessment publishAssessment(String appId, String newIdentifier, String guid) {
        checkArgument(isNotBlank(appId));
        checkArgument(isNotBlank(guid));
        
        Assessment assessmentToPublish = getAssessmentByGuid(appId, guid);
        AssessmentConfig configToPublish =  configService.getAssessmentConfig(appId, guid);
        Assessment original = Assessment.copy(assessmentToPublish);
        
        IS_ORG_MEMBER.checkAndThrow(ORG_ID ,assessmentToPublish.getOwnerId());
        
        if (StringUtils.isNotBlank(newIdentifier)) {
            assessmentToPublish.setIdentifier(newIdentifier);
        }
        // Only the original owning organization can publish new revisions of an assessment to 
        // the shared repository, so check for this as well.
        String ownerId = appId + ":" + assessmentToPublish.getOwnerId();
        String identifier = assessmentToPublish.getIdentifier();
        Assessment existing = getLatestInternal(
                SHARED_APP_ID, identifier, true).orElse(null);
        if (existing != null && !existing.getOwnerId().equals(ownerId)) {
            throw new UnauthorizedException("Assessment exists in shared library under a different " 
                    +"owner (identifier = " + identifier + ")");
        }
        int revision = (existing == null) ? 1 : (existing.getRevision()+1);
        
        // Neither of these assessments should be in an attached state because both are loaded in 
        // separate transactions that are closed in the DAO. However, the tag collections have been
        // proxied by Hibernate, and an error is thrown if you don't make copies: "Found shared 
        // references to a collection." I think this could be fixed by using transaction annotations 
        // that could be put on service methods so they can call multiple DAO calls in a session, 
        // but we'd also have to change HibernateHelper to use the existing transaction rather
        // than opening a new transaction.
        if (original.getTags() != null) {
            assessmentToPublish.setTags(ImmutableSet.copyOf(original.getTags()));    
        }
        
        assessmentToPublish.setGuid(generateGuid());
        assessmentToPublish.setRevision(revision);
        assessmentToPublish.setOriginGuid(null);
        assessmentToPublish.setOwnerId(ownerId);
        assessmentToPublish.setVersion(0L);
        
        original.setOriginGuid(assessmentToPublish.getGuid());
        
        return dao.publishAssessment(appId, original, assessmentToPublish, configToPublish);
    }
    
    /**
     * Takes an assessment in the shared context and clones it into the caller's local context, using the 
     * specified organization as the new owner of the local assessment (must be an organization the caller
     * is associated to). If the identifier already exists in this app, the revision number is adjusted
     * appropriately. The origin GUID is set to the GUID of the shared assessment.
     */
    public Assessment importAssessment(String appId, String ownerId, String newIdentifier, String guid) {
        checkArgument(isNotBlank(appId));
        checkArgument(isNotBlank(guid));

        if (ownerId == null) {
            ownerId = RequestContext.get().getCallerOrgMembership();
        }
        if (isBlank(ownerId)) {
            throw new BadRequestException("ownerId parameter is required");
        }
        IS_ORG_MEMBER.checkAndThrow(ORG_ID, ownerId);

        Assessment sharedAssessment = getAssessmentByGuid(SHARED_APP_ID, guid);
        AssessmentConfig sharedConfig = configService.getSharedAssessmentConfig(SHARED_APP_ID, guid);
        
        // Check organization because admins and superadmins can provide anything, it's not 
        // inherited from the caller's org membership (if any).
        organizationService.getOrganization(appId, ownerId);
        
        if (isNotBlank(newIdentifier)) {
            sharedAssessment.setIdentifier(newIdentifier);
        }
        // Figure out what revision this should be in the new app context if the identifier already exists
        int revision = nextRevisionNumber(appId, sharedAssessment.getIdentifier());
        sharedAssessment.setOriginGuid(sharedAssessment.getGuid());
        sharedAssessment.setGuid(generateGuid());
        sharedAssessment.setRevision(revision);
        sharedAssessment.setOwnerId(ownerId);
        
        return dao.importAssessment(appId, sharedAssessment, sharedConfig);
    }
        
    public void deleteAssessment(String appId, String guid) {
        checkArgument(isNotBlank(appId));
        checkArgument(isNotBlank(guid));
        
        Assessment assessment = getAssessmentByGuid(appId, guid);
        if (assessment.isDeleted()) {
            throw new EntityNotFoundException(Assessment.class);
        }
        IS_ORG_MEMBER.checkAndThrow(ORG_ID, assessment.getOwnerId());

        assessment.setDeleted(true);
        assessment.setModifiedOn(getModifiedOn());
        dao.updateAssessment(appId, assessment);
    }
        
    public void deleteAssessmentPermanently(String appId, String guid) {
        checkArgument(isNotBlank(appId));
        checkArgument(isNotBlank(guid));
        
        Optional<Assessment> opt = dao.getAssessment(appId, guid);
        if (opt.isPresent()) {
            dao.deleteAssessment(appId, opt.get());
        }
    }

    private Assessment createAssessmentInternal(String appId, Assessment assessment) {
        checkArgument(isNotBlank(appId));
        checkNotNull(assessment);
        
        assessment.setGuid(generateGuid());
        DateTime timestamp = getCreatedOn();
        assessment.setCreatedOn(timestamp);
        assessment.setModifiedOn(timestamp);
        assessment.setDeleted(false);
        assessment.setOriginGuid(null);
        sanitizeAssessment(assessment);

        String osName = assessment.getOsName();
        if (SYNONYMS.get(osName) != null) {
            assessment.setOsName(SYNONYMS.get(osName));
        }
        AssessmentValidator validator = new AssessmentValidator(appId, organizationService);
        Validate.entityThrowingException(validator, assessment);
        
        IS_ORG_MEMBER.checkAndThrow(ORG_ID, assessment.getOwnerId());

        AssessmentConfig config = new AssessmentConfig();
        config.setCreatedOn(timestamp);
        config.setModifiedOn(timestamp);
        config.setConfig(JsonNodeFactory.instance.objectNode());
        
        return dao.createAssessment(appId, assessment, config);
    }
    
    Optional<Assessment> getLatestInternal(String appId, String identifier, boolean includeDeleted) {
        checkArgument(isNotBlank(appId));
        checkArgument(isNotBlank(identifier));
        
        PagedResourceList<Assessment> page = dao.getAssessmentRevisions(appId, identifier, 0, 1, includeDeleted);
        if (page.getItems().isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(page.getItems().get(0));
    }
    
    /**
     * Figure out what the next revision number should be in the target app context. Include deleted 
     * revisions since the database will enforce a constraint violation if you select any existing 
     * revision number.
     */
    private int nextRevisionNumber(String appId, String identifier) {
        Optional<Assessment> opt = getLatestInternal(appId, identifier, true);
        return opt.isPresent() ? (opt.get().getRevision()+1) : 1;
    }
    
    /**
     * Assessments are publicly available and thus, could be inserted as is into web applications/
     * sites. So we scrub HTML in all the user input fields, check others during validation 
     * (identifiers), and restrict user input into yet other fields (GUIDs).
     */
    static void sanitizeAssessment(Assessment assessment) {
        assessment.setTitle(sanitizeHTML(simpleText(), assessment.getTitle()));
        assessment.setSummary(sanitizeHTML(simpleText(), assessment.getSummary()));
        assessment.setValidationStatus(sanitizeHTML(simpleText(), assessment.getValidationStatus()));
        assessment.setNormingStatus(sanitizeHTML(simpleText(), assessment.getNormingStatus()));
        if (assessment.getTags() != null) {
            assessment.setTags( 
                assessment.getTags().stream()
                    .map(string -> sanitizeHTML(none(), string))
                    .collect(toImmutableSet())
            );
        }
        if (assessment.getCustomizationFields() != null) {
            Map<String, Set<PropertyInfo>> map = new HashMap<>();
            for (Map.Entry<String, Set<PropertyInfo>> entry : assessment.getCustomizationFields().entrySet()) {                
                String key = sanitizeHTML(none(), entry.getKey());
                Set<PropertyInfo> values = entry.getValue().stream().map(prop -> {
                        return new PropertyInfo.Builder()
                                .withPropName(sanitizeHTML(none(), prop.getPropName()))
                                .withLabel(sanitizeHTML(none(), prop.getLabel()))
                                .withDescription(sanitizeHTML(none(), prop.getDescription()))
                                .withPropType(sanitizeHTML(none(), prop.getPropType()))
                                .build();
                    }).collect(toImmutableSet());
                map.put(key, values);
            }
            assessment.setCustomizationFields(map);
        }
    }
}
