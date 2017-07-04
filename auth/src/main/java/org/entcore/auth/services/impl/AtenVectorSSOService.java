package org.entcore.auth.services.impl;

import fr.wseduc.webutils.Either;
import org.entcore.auth.services.SSOService;
import org.entcore.common.neo4j.Neo4j;
import org.vertx.java.core.Handler;
import org.vertx.java.core.json.JsonArray;
import org.vertx.java.core.json.JsonObject;

import static org.entcore.common.neo4j.Neo4jResult.validResultHandler;

/**
 * Created by vogelmt on 03/07/2017.
 */
public class AtenVectorSSOService implements SSOService{

    private final Neo4j neo = Neo4j.getInstance();

    /**
     * Get student vectors for saml response : "4|"+u.lastName+"|"+u.firstName+"|"+u.externalId+"|"+s.UAI
     * @param userId neo4j userId
     * @param result handler with user vector(s)
     */
    @Override
    public void getVectorsForStudents(String userId, Handler<Either<String, JsonArray>> result) {
        String query = "MATCH u-[:IN]->()-[:DEPENDS]->(s:Structure) " +
                "WHERE u.id = {userId} " +
                "RETURN DISTINCT '4|'+u.lastName+'|'+u.firstName+'|'+u.externalId+'|'+s.UAI";

        neo.execute(query, new JsonObject().putString("userId", userId), validResultHandler(result));
    }

    /**
     * Get parent vectors for saml response : '2|'+u.lastName+'|'+u.firstName+'|'+ child.externalId+'|'+s.UAI"
     * @param userId neo4j userId
     * @param result handler with user vector(s) (one by child)
     */
    @Override
    public void getVectorsForParents(String userId, Handler<Either<String, JsonArray>> result) {

        String query = "MATCH (child: User)-[:RELATED]->u-[:IN]->()-[:DEPENDS]->(s:Structure) " +
                "WHERE u.id = {userId} " +
                "RETURN '2|'+u.lastName+'|'+u.firstName+'|'+ child.externalId+'|'+s.UAI";

        neo.execute(query, new JsonObject().putString("userId", userId), validResultHandler(result));
    }

}
