package org.entcore.auth.services;

import fr.wseduc.webutils.Either;
import org.vertx.java.core.Handler;
import org.vertx.java.core.json.JsonArray;

/**
 * Created by vogelmt on 03/07/2017.
 */
public interface SSOService {

    public void getVectorsForStudents(String userId, Handler<Either<String, JsonArray>> result);

    public void getVectorsForParents(String userId, Handler<Either<String, JsonArray>> result);
}


