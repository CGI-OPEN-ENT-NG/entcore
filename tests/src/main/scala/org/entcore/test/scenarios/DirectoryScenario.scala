package org.entcore.test.scenarios

import io.gatling.core.Predef._
import io.gatling.http.Predef._

import net.minidev.json.{JSONValue, JSONObject, JSONArray}
import scala.collection.JavaConverters._

object DirectoryScenario {

	val scn = exec(http("Get admin page")
			.get("""/admin""")
		.check(status.is(302)))
		.exec(http("Authenticate admin user")
			.post("""/auth/login""")
			.formParam("""callBack""", """http%3A%2F%2Flocalhost%3A8080%2Fadmin""")
			.formParam("""email""", """tom.mate""")
			.formParam("""password""", """password""")
		.check(status.is(302)))
		.exec(http("Get admin page")
			.get("""/directory/admin-console""")
		.check(status.is(200)))
    .exec(http("List admin")
    .get("""/directory/user/admin/list""")
    .check(status.is(200),
      jsonPath("$").find.transformOption(_.map{ j =>
      JSONValue.parse(j).asInstanceOf[JSONArray].asScala.toList
        .filter(_.asInstanceOf[JSONObject].get("source") == "MANUAL")
        .map(_.asInstanceOf[JSONObject].get("id"))
        .mkString("userId=", "&userId=", "")
    }).saveAs("manualIds"),
      jsonPath("$").find.transformOption(_.map{ j =>
        JSONValue.parse(j).asInstanceOf[JSONArray].asScala.toList
          .find(_.asInstanceOf[JSONObject].get("lastName") == "PIREZ")
          .map(_.asInstanceOf[JSONObject].get("id")).getOrElse("")
      }).saveAs("rachelId"),
      jsonPath("$").find.transformOption(_.map{ j =>
        JSONValue.parse(j).asInstanceOf[JSONArray].asScala.toList
          .exists(_.asInstanceOf[JSONObject].get("lastName") == "PIRES")
      }).saveAs("twoRachelExists")))
    .doIf (session => session("manualIds").as[String].trim != "userId=") {
      exec(http("Delete manual users")
        .delete("""/directory/user?${manualIds}""")
        .check(status.is(200)))
    }
    .doIf (session => session("rachelId").as[String].nonEmpty && !session("twoRachelExists").as[Boolean]) {
      exec(http("Update user infos")
        .put("""/directory/user/${rachelId}""")
        .header("Content-Type", "application/json")
        .body(StringBody("""{"firstName": "Rachelle", "lastName": "PIRES"}"""))
        .check(status.is(200)))
    }
		.exec(http("List Schools")
			.get("""/directory/api/ecole""")
		.check(status.is(200), jsonPath("$.status").is("ok"),
      jsonPath("$.result.*.id").find.saveAs("schoolId")))
		.exec(http("List classes")
			.get("""/directory/api/classes?id=${schoolId}""")
		.check(status.is(200), jsonPath("$.status").is("ok"),
      jsonPath("$.result.*.classId").find.saveAs("classId")))
    .exec(http("List students in class")
      .get("""/directory/api/personnes?id=${classId}&type=Student""")
    .check(status.is(200), jsonPath("$.status").is("ok"),
      jsonPath("$.result.*.userId").find.saveAs("childrenId")))
		.exec(http("Create manual teacher")
			.post("""/directory/api/user""")
			.formParam("""classId""", """${classId}""")
			.formParam("""lastname""", "Devost")
			.formParam("""firstname""", """Julie""")
			.formParam("""type""", """Teacher""")
		.check(status.is(200)))
    .exec(http("Create manual student")
      .post("""/directory/api/user""")
      .formParam("""classId""", """${classId}""")
      .formParam("""lastname""", "Monjeau")
      .formParam("""firstname""", """Lundy""")
      .formParam("""birthDate""", """1970-01-01""")
      .formParam("""type""", """Student""")
    .check(status.is(200)))
    .exec(http("Create manual parent")
      .post("""/directory/api/user""")
      .formParam("""classId""", """${classId}""")
      .formParam("""lastname""", "Bondy")
      .formParam("""firstname""", """Astrid""")
      .formParam("""type""", """Relative""")
      .formParam("""childrenIds""", """${childrenId}""")
    .check(status.is(200)))
    .exec(http("Create manual Guest")
      .post("""/directory/api/user""")
      .formParam("""classId""", """${classId}""")
      .formParam("""lastname""", "Tillman")
      .formParam("""firstname""", """Lizzie""")
      .formParam("""type""", """Guest""")
    .check(status.is(200)))
    .exec(http("Create manual Guest")
    .post("""/directory/api/user""")
      .formParam("""structureId""", """${schoolId}""")
      .formParam("""lastname""", "True")
      .formParam("""firstname""", """Krysten""")
      .formParam("""type""", """Guest""")
      .check(status.is(200)))
    .exec(http("List persons in class")
      .get("""/directory/api/personnes?id=${classId}""")
      .check(status.is(200), jsonPath("$.status").is("ok"),
      jsonPath("$.result").find.transformOption(_.map(res => {
        val json = JSONValue.parse(res).asInstanceOf[JSONObject]
        json.values.asScala.foldLeft[List[(String, String)]](Nil){(acc, c) =>
          val user = c.asInstanceOf[JSONObject]
          user.get("lastName").asInstanceOf[String] match {
            case "Devost" | "Monjeau" | "Bondy" if user.get("code") != null =>
              (user.get("type").asInstanceOf[String], user.get("userId").asInstanceOf[String]) :: acc
            case _ => acc
          }
        }.toMap
      })).saveAs("createdUserIds")))
    .exec{session =>
      val uIds = session("createdUserIds").as[Map[String, String]]
      session.set("teacherId", uIds.get("Teacher").get).set("studentId", uIds.get("Student").get)
        .set("relativeId", uIds.get("Relative").get)
        .set("now", System.currentTimeMillis())
    }
    .exec(http("Teacher details")
      .get("""/directory/api/details?id=${teacherId}""")
      .check(status.is(200), jsonPath("$.status").is("ok"),
        jsonPath("$.result.*.login").find.saveAs("teacherLogin"),
        jsonPath("$.result.*.code").find.saveAs("teacherCode")))
    .exec(http("Student details")
    .get("""/directory/api/details?id=${studentId}""")
      .check(status.is(200), jsonPath("$.status").is("ok"),
        jsonPath("$.result.*.login").find.saveAs("studentLogin"),
        jsonPath("$.result.*.code").find.saveAs("studentCode")))

    // create function
    .exec(http("Create function")
      .post("""/directory/function/Teacher""")
      .header("Content-Type", "application/json")
      .body(StringBody("""{"externalId": "ADMIN_LOCAL_${now}", "name": "AdminLocal"}"""))
      .check(status.is(201), jsonPath("$.id").find.saveAs("function-id")))

    .exec(http("Create function")
      .post("""/directory/function/Teacher""")
      .header("Content-Type", "application/json")
      .body(StringBody("""{"externalId": "CLASS_ADMIN_${now}", "name": "Class Admin"}"""))
      .check(status.is(201), jsonPath("$.id").find.saveAs("function-id2")))

    .exec(http("Create function")
      .post("""/directory/function/Teacher""")
      .header("Content-Type", "application/json")
      .body(StringBody("""{"externalId": "DELETE_${now}", "name": "To delete"}"""))
      .check(status.is(201), jsonPath("$.id").find.saveAs("function-delete")))

    // add user function

    .exec(http("User add function ")
      .post("""/directory/user/function/${teacherId}""")
      .header("Content-Type", "application/json")
      .body(StringBody("""{"functionCode": "ADMIN_LOCAL_${now}", "scope": ["${schoolId}"]}"""))
      .check(status.is(200)))

    .exec(http("User add function ")
      .post("""/directory/user/function/${teacherId}""")
      .header("Content-Type", "application/json")
      .body(StringBody("""{"functionCode": "ADMIN_LOCAL", "scope": ["${schoolId}"], "inherit":"sc"}"""))
      .check(status.is(200)))

    // remove user from group
//    .exec(http("Remove user from group")
//      .delete("""/directory/user/group/${teacherId}/${function-group-id-delete}""")
//      .header("Content-Length", "0")
//      .check(status.is(200)))
//
//    // remove user function
//    .exec(http("Remove user function")
//      .delete("""/directory/user/function/${teacherId}/DELETE_${now}""")
//      .check(status.is(200)))
//
//    // Delete function group
//    .exec(http("Delete function group")
//      .delete("""/directory/functiongroup/${function-group-id-delete}""")
//      .check(status.is(204)))

    // Delete function
//    .exec(http("Delete function")
//      .delete("""/directory/function/DELETE_${now}""")
//      .check(status.is(204)))

  // create group
  .exec(http("Create group")
    .post("""/directory/group""")
    .header("Content-Type", "application/json")
    .body(StringBody("""{"name": "Group with rattachment"}"""))
    .check(status.is(201), jsonPath("$.id").find.saveAs("manuel-group-id")))

  .exec(http("update group")
    .put("""/directory/group/${manuel-group-id}""")
    .header("Content-Type", "application/json")
    .body(StringBody("""{"name": "Group with rattachment updated"}"""))
    .check(status.is(200), jsonPath("$.id").find.is("${manuel-group-id}")))

  // add user to group
  .exec(http("add user to group")
    .post("""/directory/user/group/${teacherId}/${manuel-group-id}""")
    .header("Content-Length", "0")
    .check(status.is(200)))

  .exec(http("add user to group")
    .post("""/directory/user/group/${studentId}/${manuel-group-id}""")
    .header("Content-Length", "0")
    .check(status.is(200)))

  .exec(http("Get user")
    .get("""/directory/user/${studentId}""")
    .check(status.is(200), jsonPath("$.id").find.is("${studentId}"),
      jsonPath("$.type[0]").find.is("Student"),
      jsonPath("$.type").find.transformOption(_.map(res => JSONValue.parse(res).asInstanceOf[JSONArray].size())).is(1)))

//    .exec(http("Create structure")
//    .post("""/directory/school?setDefaultRoles=true""")
//    .header("Content-Type", "application/json")
//    .body(StringBody("""{"name":"Manual school","UAI":"0301072C"}"""))
//    .check(status.is(201), jsonPath("$.id").saveAs("manualStructureId")))
    .exec(http("Create class")
    .post("""/directory/class/${schoolId}?setDefaultRoles=true""")
    .header("Content-Type", "application/json")
    .body(StringBody("""{"name": "Manual class ${now}"}"""))
    .check(status.is(201)))
}
