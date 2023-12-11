package org.entcore.cas.services;

import fr.wseduc.cas.entities.User;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.entcore.cas.mapping.Mapping;
import org.entcore.common.events.EventStore;
import org.entcore.common.events.EventStoreFactory;
import org.entcore.common.user.UserInfos;
import org.entcore.common.user.UserUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.entcore.common.events.EventHelper;


public class JupyterRegisteredService extends UidRegisteredService{

    private final EventStore eventStore = EventStoreFactory.getFactory().getEventStore(this.getClass().getSimpleName());

    @Override
    protected void prepareUserCas20(User user, String userId, String service, JsonObject data, Document doc, List<Element> additionnalAttributes) {
        super.prepareUserCas20(user, userId, service, data, doc, additionnalAttributes);
        UserUtils.getUserInfos(eb, userId, userInfos -> {
            if (userInfos != null) {
                createStatsEvent(userInfos, service);
            }
        });
    }

    private void createStatsEvent(UserInfos userInfos, String service){
        final Optional<Mapping> mapping = foundMappingByService(new HashSet<>(userInfos.getStructures()), service);

        final JsonObject event = new JsonObject().put("service", service).put("connector-type", "Cas");
        event.put("cas-type", mapping.map(Mapping::getType).orElse("unknown"));
        eventStore.createAndStoreEvent(EventHelper.ACCESS_EVENT, userInfos, event);
    }

}
