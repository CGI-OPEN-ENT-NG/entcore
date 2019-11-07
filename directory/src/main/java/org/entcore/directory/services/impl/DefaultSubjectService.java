package org.entcore.directory.services.impl;

import fr.wseduc.webutils.Either;
import fr.wseduc.webutils.collections.Joiner;
import io.vertx.core.Handler;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.entcore.common.neo4j.Neo4j;
import org.entcore.directory.services.SubjectService;

import static org.entcore.common.neo4j.Neo4jResult.*;

public class DefaultSubjectService implements SubjectService {

    private final Neo4j neo = Neo4j.getInstance();
    private final EventBus eventBus;

    public DefaultSubjectService(EventBus eventBus) {
        this.eventBus = eventBus;
    }

    @Override
    public void listAdmin(String structureId, Handler<Either<String, JsonArray>> results){
        final JsonObject params = new JsonObject().put("structureId", structureId);
        String query = "MATCH (s:Structure {id : {structureId}})<-[:SUBJECT]-(sub:Subject)"+
                "return sub.id as id, sub.code as code, sub.label as label";

        neo.execute(query, params, validResultHandler(results));
    }

    @Override
    public void createOrUpdateManual(JsonObject subject, Handler<Either<String, JsonObject>> result) {
        JsonObject params = new JsonObject()
                .put("structureId", subject.getString("structureId"))
                .put("label", subject.getString("label"))
                .put("code", subject.getString("code"))
                .put("source", "MANUAL");

        String query =  "MATCH (s: Structure {id : {structureId} })"+
                "MERGE (s)<-[r: SUBJECT]-(sub:Subject { code: {code}, label: {label}, source: {source} }) "+
                "ON CREATE SET sub.id = id(sub) + '-' + timestamp()" +
                "RETURN sub.id as id, sub.label as label, sub.code as code, sub.source as source;";

        neo.execute(query, params, validUniqueResultHandler(result));
    }
}
