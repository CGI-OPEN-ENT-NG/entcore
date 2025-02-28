package org.entcore.directory.services.impl;

import com.mongodb.client.model.Filters;
import fr.wseduc.mongodb.MongoDb;
import fr.wseduc.mongodb.MongoQueryBuilder;
import fr.wseduc.mongodb.MongoUpdateBuilder;
import fr.wseduc.webutils.Either;
import org.bson.conversions.Bson;
import org.entcore.common.service.impl.MongoDbCrudService;
import org.entcore.directory.pojo.Slot;
import org.entcore.directory.services.SlotProfileService;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.util.UUID;

import static org.entcore.common.mongodb.MongoDbResult.*;


public class DefaultSlotProfileService extends MongoDbCrudService implements SlotProfileService {


    public DefaultSlotProfileService(String collection) {
        super(collection);
    }

    @Override
    public void createSlot(String idSlotProfile, JsonObject slot, Handler<Either<String, JsonObject>> handler) {
        // Query
        Bson query = Filters.eq("_id", idSlotProfile);

        // Set UUID
        slot.put(Slot.ID, UUID.randomUUID().toString());

        // Modifier
        MongoUpdateBuilder modifier = new MongoUpdateBuilder();
        modifier.push("slots", slot);
        modifier.set("modified", MongoDb.now());
        mongo.update(this.collection, MongoQueryBuilder.build(query), modifier.build(), validActionResultHandler(handler));
    }

    @Override
    public void listSlots(String idSlotProfile, Handler<Either<String, JsonObject>> handler) {
        // Query
        Bson query = Filters.eq("_id", idSlotProfile);

        // Projection
        JsonObject projection = new JsonObject().put("slots", 1).put("_id", 0);
        mongo.findOne(this.collection, MongoQueryBuilder.build(query), projection, validResultHandler(handler));
    }

    @Override
    public void listSlotProfilesByStructure(String structureId, Handler<Either<String, JsonArray>> handler) {
        // Query
        JsonObject match = new JsonObject().put("schoolId", structureId);

        JsonObject sort = new JsonObject().put("name", 1);

        // Projection
        JsonObject projection = new JsonObject()
                .put("name", 1)
                .put("schoolId", 1)
                .put("slots", 1);
        mongo.find(this.collection, match, sort, projection, validResultsHandler(handler));
    }

    @Override
    public void updateSlot(String idSlotProfile, JsonObject slot, Handler<Either<String, JsonObject>> handler) {
        // Query
        Bson query = Filters.and(
                Filters.eq("_id", idSlotProfile),
                Filters.elemMatch("slots", Filters.eq(Slot.ID, slot.getString(Slot.ID)))
        );

        // Modifier
        MongoUpdateBuilder modifier = new MongoUpdateBuilder();
        modifier.set("slots.$", slot);
        modifier.set("modified", MongoDb.now());
        mongo.update(this.collection, MongoQueryBuilder.build(query), modifier.build(), validActionResultHandler(handler));
    }

    @Override
    public void deleteSlotFromSlotProfile(String idSlotProfile, String idSlot, Handler<Either<String, JsonObject>> handler) {
        // Query
        Bson query = Filters.eq("_id", idSlotProfile);

        // Modifier
        MongoUpdateBuilder modifier = new MongoUpdateBuilder();
        modifier.pull("slots", MongoQueryBuilder.build(Filters.eq(Slot.ID, idSlot)));
        modifier.set("modified", MongoDb.now());
        mongo.update(this.collection, MongoQueryBuilder.build(query), modifier.build(), validActionResultHandler(handler));
    }

    @Override
    public void updateSlotProfile(String idSlotProfile, String name, Handler<Either<String, JsonObject>> handler) {
        // Query
        Bson query = Filters.eq("_id", idSlotProfile);

        // Modifier
        MongoUpdateBuilder modifier = new MongoUpdateBuilder();
        modifier.set("name", name);
        modifier.set("modified", MongoDb.now());
        mongo.update(this.collection, MongoQueryBuilder.build(query), modifier.build(), validActionResultHandler(handler));
    }

    @Override
    public void deleteSlotProfile(String idSlotProfile, Handler<Either<String, JsonObject>> handler) {
        // Query
        Bson query = Filters.eq("_id", idSlotProfile);
        mongo.delete(this.collection, MongoQueryBuilder.build(query), validActionResultHandler(handler));
    }
}
