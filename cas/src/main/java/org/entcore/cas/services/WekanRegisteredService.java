package org.entcore.cas.services;

import fr.wseduc.cas.entities.User;
import io.vertx.core.json.JsonObject;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.util.List;

public class WekanRegisteredService extends AbstractCas20ExtensionRegisteredService {
    @Override
    protected void prepareUserCas20(User user, String userId, String service, JsonObject data, Document doc, List<Element> additionnalAttributes) {
        try {
            user.setUser(data.getString("id"));
            additionnalAttributes.add(createTextElement("givenName", data.getString("firstName"), doc));
            additionnalAttributes.add(createTextElement("sn", data.getString("lastName"), doc));
            additionnalAttributes.add(createTextElement("fullName", data.getString("displayName"), doc));
            additionnalAttributes.add(createTextElement("mail", data.getString("email"), doc));
        } catch (Exception e) {
            log.error("Failed to transform user for WekanRegisteredService", e);
        }
    }
}
