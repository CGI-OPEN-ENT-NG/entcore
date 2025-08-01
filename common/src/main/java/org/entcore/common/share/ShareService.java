/* Copyright © "Open Digital Education", 2014
 *
 * This program is published by "Open Digital Education".
 * You must indicate the name of the software and the company in any production /contribution
 * using the software and indicate on the home page of the software industry in question,
 * "powered by Open Digital Education" with a reference to the website: https://opendigitaleducation.com/.
 *
 * This program is free software, licensed under the terms of the GNU Affero General Public License
 * as published by the Free Software Foundation, version 3 of the License.
 *
 * You can redistribute this application and/or modify it since you respect the terms of the GNU Affero General Public License.
 * If you modify the source code and then use this modified source code in your creation, you must make available the source code of your modifications.
 *
 * You should have received a copy of the GNU Affero General Public License along with the software.
 * If not, please see : <http://www.gnu.org/licenses/>. Full compliance requires reading the terms of this license and following its directives.

 *
 */

package org.entcore.common.share;

import fr.wseduc.webutils.Either;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.entcore.common.user.UserInfos;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface ShareService {

	/**
	 * Parameter use in userUtils to add a filter on the visible query on group or ids.
	 * This constant is used as a key in additionalParams to pass the list of ids to check
	 */
	String EXPECTED_IDS_USERS_GROUPS = "expectedIdsOfUsersAndGroups";

	void inheritShareInfos(String userId, String resourceId, String acceptLanguage, String search,
			Handler<Either<String, JsonObject>> handler);

	void shareInfosWithoutVisible(String userId, String resourceId, Handler<Either<String, JsonArray>> handler);

	void shareInfos(String userId, String resourceId, String acceptLanguage, String search,
					Handler<Either<String, JsonObject>> handler);

	void shareInfos(String userId, String resourceId, String acceptLanguage, ShareInfosQuery query,
			Handler<Either<String, JsonObject>> handler);

	void groupShare(String userId, String groupShareId, String resourceId, List<String> actions,
			Handler<Either<String, JsonObject>> handler);

	void userShare(String userId, String userShareId, String resourceId, List<String> actions,
			Handler<Either<String, JsonObject>> handler);

	void removeGroupShare(String groupId, String resourceId, List<String> actions,
			Handler<Either<String, JsonObject>> handler);

	void removeUserShare(String userId, String resourceId, List<String> actions,
			Handler<Either<String, JsonObject>> handler);

	//static void removeShareMetadata(JsonObject data);

	Future<JsonObject> share(String userId, String resourceId, JsonObject share, Handler<Either<String, JsonObject>> handler);

	default Future<JsonObject> share(UserInfos user, String resourceId, JsonObject share, Handler<Either<String, JsonObject>> handler){
		return share(user.getUserId(), resourceId, share, handler);
	}

	default void findUserIdsForShare(String resourceId, String userId,
							 Handler<AsyncResult<Set<String>>> h){
		findUserIdsForShare(resourceId, userId, Optional.empty(), h);
	}
	/**
	 * 
	 * @param resourceId id of the resource
	 * @param userId     id of current user
	 * @param actions	 optional actions list to filter only users having action
	 * @param h          handler that emit the set of users id concerned by the
	 *                   share array
	 */
	void findUserIdsForShare(String resourceId, String userId, Optional<Set<String>> actions,
			Handler<AsyncResult<Set<String>>> h);

	void findUserIdsForInheritShare(String resourceId, String userId, Optional<Set<String>> actions,
			Handler<AsyncResult<Set<String>>> h);

	/**
	 * Gets the id of the owner of a resource.
	 * So far, this method is only used to check if it is okay to add the manager rights
	 * to a user who is not visible to the user trying to modify the shares of a resource.
	 * @param resourceId Id of the resource of which we want the owner's id
	 * @return Id of the owner or empty string if the owner could not be found
	 */
	default Future<String> getResourceOwnerUserId(final String resourceId) {
		return Future.succeededFuture("");
	}
}
