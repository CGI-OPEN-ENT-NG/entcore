package org.entcore.test;

import fr.wseduc.webutils.security.BCrypt;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.entcore.common.neo4j.Neo4j;
import org.entcore.common.user.UserInfos;

import java.util.ArrayList;
import java.util.UUID;

public class UserbookTestHelper {
    private final Vertx vertx;
    private final TestHelper test;

    UserbookTestHelper(TestHelper t, Vertx v) {
        this.vertx = v;
        this.test = t;
    }

    public UserbookTestHelper createMock(Handler<Message<JsonObject>> handler) {
        vertx.eventBus().consumer("userbook.preferences", handler);
        return this;
    }

    public Future<Integer> setQuotaForUserId(String userId, Long quota) {
        Future<Integer> future = Future.future();
        test.database().executeNeo4jWithUniqueResult(
                "MATCH (u:User) WHERE u.id={userId} MERGE (u)-[:USERBOOK]->(ub:UserBook { userid : {userId}}) SET ub.quota = {quota} RETURN ub.quota as quota",
                new JsonObject().put("userId", userId).put("quota", quota)).setHandler(resCount -> {
            if (resCount.succeeded()) {
                future.complete(resCount.result().getInteger("quota").intValue());
            } else {
                future.fail(resCount.cause());
            }
        });
        return future;
    }

    public Future<Integer> setStorageForUser(String userId, Long storage) {
        Future<Integer> future = Future.future();
        test.database().executeNeo4jWithUniqueResult(
                "MATCH (u:User) WHERE u.id={userId} MERGE (u)-[:USERBOOK]->(ub:UserBook { userid : {userId}}) SET ub.storage = {storage} RETURN ub.storage as storage",
                new JsonObject().put("userId", userId).put("storage", storage)).setHandler(resCount -> {
            if (resCount.succeeded()) {
                future.complete(resCount.result().getInteger("storage").intValue());
            } else {
                future.fail(resCount.cause());
            }
        });
        return future;
    }
}