package org.sagebionetworks.bridge.models;
// TODO: check if we need to refactor this into /models/reports/

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import org.sagebionetworks.bridge.dynamodb.DynamoParticipantData;
import org.sagebionetworks.bridge.json.BridgeTypeName;

@BridgeTypeName("ParticipantData")
@JsonDeserialize(as= DynamoParticipantData.class)
public interface ParticipantData extends BridgeEntity{

    static ParticipantData create(){
        return new DynamoParticipantData();
    }

    String getHealthCode();
    void setHealthCode(String healthCode);

    String getIdentifier();
    void setIdentifier(String identifier);

    JsonNode getData();
    void setData(JsonNode data);

    Long getVersion();
    void setVersion(Long version);
}
