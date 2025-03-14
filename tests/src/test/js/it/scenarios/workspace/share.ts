import { check, sleep } from "k6";
import {chai, describe } from "https://jslib.k6.io/k6chaijs/4.3.4.0/index.js";

import {
  getTeacherRole,
  authenticateWeb,
  getStudentRole,
  getParentRole,
  getUsersOfSchool,
  createAndSetRole,
  linkRoleToUsers,
  uploadFile,
  shareFile,
  getShares,
  getBroadcastGroup,
  createBroadcastGroup,
  addCommRuleToGroup,
  addCommunicationBetweenGroups,
  attachStructureAsChild,
  switchSession,
  WS_READER_SHARE,
  WS_MANAGER_SHARE,
  triggerImport,
  createEmptyStructure,
  initStructure,
  getRandomUser,
  getRandomUserWithProfile,
  Session,
  createUser,
  ShareBookMarkCreationRequest,
  createUserAndGetData,
  createShareBookMarkOrFail,
  getProfileGroupOfStructure,
  getProfileGroupOfStructureByType,
  removeCommunicationBetweenGroups,
  getGuestRole
} from "../../../node_modules/edifice-k6-commons/dist/index.js";

const aafImport = (__ENV.AAF_IMPORT || "true") === "true";
const aafImportPause =  parseInt(__ENV.AAF_IMPORT_PAUSE || "10");
const maxDuration = __ENV.MAX_DURATION || "1m";
const schoolName = __ENV.DATA_SCHOOL_NAME || "General";
const schoolName2 = `${schoolName}-2`
const dataRootPath = __ENV.DATA_ROOT_PATH;
const gracefulStop = parseInt(__ENV.GRACEFUL_STOP || "2s");

const broadcastGroupName = "IT - liste de diffusion profs"
const broadcastGroupNameChapeau = "IT - Chapeau liste de diffusion profs"

export const options = {
  setupTimeout: "1h",
  thresholds: {
    checks: ["rate == 1.00"],
  },
  scenarios: {
    shareFile: {
      executor: "per-vu-iterations",
      vus: 1,
      maxDuration: maxDuration,
      gracefulStop,
    },
  },
};

let fileToUpload;
try {
  fileToUpload = open(`${dataRootPath}/workspace/random_text_file.txt`, "b");
} catch(e) {
  fileToUpload = open(`${dataRootPath}/data/workspace/random_text_file.txt`, "b");
}


export function setup() {
  let structure1;
  let structure2;
  let chapeau;
  describe("[Workspace-Init] Initialize data", () => {
    const session: Session = <Session>authenticateWeb(__ENV.ADMC_LOGIN, __ENV.ADMC_PASSWORD);
    chapeau = createEmptyStructure(`Chapeau - ${schoolName}`, false)
    const commonBG = createBroadcastGroup(broadcastGroupNameChapeau, chapeau)
    structure1 = initSchool(schoolName)
    structure2 = initSchool(schoolName2)
    attachStructureAsChild(chapeau, structure1)
    attachStructureAsChild(chapeau, structure2)
    const teacherRole1 = getTeacherRole(structure1)
    const teacherRole2 = getTeacherRole(structure2)
    const parentRole2 = getParentRole(structure2)
    const studentRole2 = getStudentRole(structure2)
    const groupIds = [
      teacherRole1.id,
      teacherRole2.id
    ]
    addCommRuleToGroup(commonBG.id, groupIds)
    addCommRuleToGroup(parentRole2.id, [teacherRole1.id])
    addCommRuleToGroup(studentRole2.id, [parentRole2.id])
    if(aafImport) {
      triggerImport()
      sleep(aafImportPause)
    }
  });
  return { structure1, structure2, chapeau};
}

function initSchool(structureName) {
  const structure = initStructure(structureName);
  const role = createAndSetRole('Espace documentaire');
  const groups = [
    `Teachers from group ${structure.name}.`,
    `Enseignants du groupe ${structure.name}.`,
    `Students from group ${structure.name}.`,
    `Élèves du groupe ${structure.name}.`,
    `Relatives from group ${structure.name}.`,
    `Parents du groupe ${structure.name}.`
  ]
  linkRoleToUsers(structure, role, groups);
  const teacherRole = getTeacherRole(structure)
  const broadcastGroup = createBroadcastGroup(broadcastGroupName, structure);
  addCommunicationBetweenGroups(teacherRole.id, broadcastGroup.id)
  return structure
}

export default (data) => {
  testSharesViaBroadcastGroupInSameSchool(data)
  testSharesViaBroadcastGroupInDifferentSchools(data)
  testSharesViaProfileGroupInDifferentSchools(data)
  testSharesViaFavouritesWithUsersWhoBecameInvisible(data)
};


function checkShareOk(res, checkName) {
  const checks = {}
  checks[checkName] = (r) => r.status === 200
  const ok = check(res, checks);
  if(!ok) {
    console.error(checkName, res)
  }
}

function checkShareKo(res, checkName) {
  const checks = {}
  checks[`${checkName} - HTTP status`] = (r) => r.status !== 200;
  checks[`${checkName} - insufficient rights error returned`] = (r) => {
    try {
      return JSON.parse(r.body).error === 'insufficient.rights.to.modify.shares'
    } catch (e) {
      return true;
    }
  }
  const ok = check(res, checks)
  if(!ok) {
    console.error(checkName, res)
  }
}

function checkPresentShares(res, sharesType, expectedSharesToBePresent, checkName) {
  const checks = {}
  checks[checkName] = (r) => {
    return expectedSharesToBePresent
        .every(expectedShare => r[sharesType].visibles.map(actualShare => actualShare.id).includes(expectedShare))
  }
  const ok = check(res, checks)
  if (!ok) {
    console.error(checkName, res)
  }
}

function checkAbsentShares(res, sharesType, expectedSharesToBeAbsent, checkName) {
  const checks = {}
  checks[checkName] = (r) => {
    return expectedSharesToBeAbsent
        .every(expectedShare => !r[sharesType].visibles.map(actualShare => actualShare.id).includes(expectedShare))
  }
  const ok = check(res, checks)
  if (!ok) {
    console.error(checkName, res)
  }
}

function testSharesViaProfileGroupInDifferentSchools(data) {
  const { structure1, structure2 } = data;
  describe('[Workspace] Test shares via profile groups in two different schools', () => {
    let res;
    authenticateWeb(__ENV.ADMC_LOGIN, __ENV.ADMC_PASSWORD);
    const users1 = getUsersOfSchool(structure1);
    const users2 = getUsersOfSchool(structure2);
    const parentRole2 = getParentRole(structure2)
    const studentRole2 = getStudentRole(structure2)
    const teacher1 = getRandomUserWithProfile(users1, 'Teacher');
    const teacher12 = getRandomUserWithProfile(users1, 'Teacher', [teacher1]);
    const parent = getRandomUserWithProfile(users2, 'Relative')
    console.log("Teacher 1 - ", teacher1.login);
    console.log("Teacher 1.2 - ", teacher12.login);
    console.log("Parent - ", parent.login);
    // Teacher upload a file
    authenticateWeb(teacher1.login, 'password');
    const uploadedFile = uploadFile(fileToUpload);
    const fileId = uploadedFile._id;
    // Share this file to parents 2 as a manager -> ok
    const shares = {bookmarks: {}, groups: {}, users: {}}
    shares.groups[parentRole2.id] = WS_MANAGER_SHARE;
    res = shareFile(fileId, shares);
    checkShareOk(res, 'teacher of school 1 shares to parents group of school 2')
    res = getShares(fileId)
    checkPresentShares(res, "groups", [parentRole2.id], "parents group of school 2 appears in shares")
    // Parent of school 2 tries to share it to students of school 2 -> ok
    let parentSession = <Session>authenticateWeb(parent.login, 'password');
    shares.groups[studentRole2.id] = WS_READER_SHARE;
    res = shareFile(fileId, shares)
    checkShareOk(res, 'parent of school 2 shares to students of school 2')
    res = getShares(fileId)
    checkPresentShares(res, "groups", [studentRole2.id], "student group of school 2 appears in shares")
    // Teacher 2 of school 1 tries to modify shares of students of school 2 -> ko
    shares.groups[studentRole2.id] = WS_MANAGER_SHARE;
    authenticateWeb(teacher12.login, 'password')
    res = shareFile(fileId, shares)
    checkShareKo(res, 'other teacher of school 1 tries to modify shares of student of school 2')
    // Parent of school 2 tries to do the same thing -> ok
    switchSession(parentSession);
    res = shareFile(fileId, shares)
    checkShareOk(res, 'parent of school 2 tries to modify shares of student of school 2')
    // Parent of school 2 tries to add teacher of school 1 (i.e. the creator) as a manager -> ok
    shares.users[teacher1.id] = WS_MANAGER_SHARE
    res = shareFile(fileId, shares)
    checkShareOk(res, 'parent of school 2 tries to add creator as a manager')
  })
}
function testSharesViaBroadcastGroupInDifferentSchools(data) {
  const { structure1, structure2, chapeau } = data;
  describe('[Workspace] Test shares to broadcast group in two different schools', () => {
    let res;
    authenticateWeb(__ENV.ADMC_LOGIN, __ENV.ADMC_PASSWORD);
    const users1 = getUsersOfSchool(structure1);
    const users2 = getUsersOfSchool(structure2);
    const broadcastGroup = getBroadcastGroup(broadcastGroupNameChapeau, chapeau);
    const broadcastGroupSchool1 = getBroadcastGroup(broadcastGroupName, structure1);
    const teachers1 = users1.filter(u => u.type === 'Teacher')
    const teachers2 = users2.filter(u => u.type === 'Teacher')
    const students1 = users1.filter(u => u.type === 'Student')
    const students2 = users2.filter(u => u.type === 'Student')
    const teacher1 = getRandomUser(teachers1);
    const teacher2 = getRandomUser(teachers2);
    const classId1 = teacher1.classes[0].id
    const classId2 = teacher2.classes[0].id
    const classUsers1 = students1.filter(u => u.classes.map(c => c.id).indexOf(classId1) >= 0);
    const classUsers2 = students2.filter(u => u.classes.map(c => c.id).indexOf(classId2) >= 0);
    const firstStudent1 = getRandomUser(classUsers1);
    const firstStudent2 = getRandomUser(classUsers2);
    console.log("Teacher 1 - ", teacher1.login);
    console.log("Teacher 2 - ", teacher2.login);
    console.log("Student 1 - ", firstStudent1.login);
    console.log("Student 2 - ", firstStudent2.login);
    // Teacher upload a file
    authenticateWeb(teacher1.login, 'password');
    const uploadedFile = uploadFile(fileToUpload);
    const fileId = uploadedFile._id;
    // Share this file to teacher 2 as a manager
    const shares = {bookmarks: {}, groups: {}, users: {}}
    shares.groups[broadcastGroup.id] = WS_MANAGER_SHARE;
    res = shareFile(fileId, shares);
    checkShareOk(res, 'teacher of school 1 shares to chapeau broadcast group')
    res = getShares(fileId)
    checkPresentShares(res, "groups", [broadcastGroup.id], "chapeau broadcast group appears in shares")
    // Teacher of school 2 shares it to one of the student of school 2 => ok
    authenticateWeb(teacher2.login, 'password');
    shares.users[firstStudent2.id] = WS_MANAGER_SHARE;
    res = shareFile(fileId, shares);
    checkShareOk(res, 'teacher of school 2 shares to student of school 2')
    res = getShares(fileId)
    checkPresentShares(res, "users", [firstStudent2.id], "student of school 2 appears in shares")
    // Student of school 2 tries to share it to student of school 1 -> ko
    authenticateWeb(firstStudent1.login, 'password');
    shares.users[firstStudent1.id] = WS_READER_SHARE;
    res = shareFile(fileId, shares)
    checkShareKo(res, 'student of school 2 shares to student of school 1')
    delete shares.users[firstStudent1.id]
    // Student of school 2 tries to share it to broadcast group of school 1 -> ko
    shares.groups[broadcastGroupSchool1.id] = WS_MANAGER_SHARE;
    res = shareFile(fileId, shares)
    checkShareKo(res, 'student of school 2 shares to broadcast group of school 1')
  })
}

function testSharesViaBroadcastGroupInSameSchool(data) {
  const { structure1 } = data;
  describe('[Workspace] Test shares to broadcast group in the same school', () => {
    let res;
    authenticateWeb(__ENV.ADMC_LOGIN, __ENV.ADMC_PASSWORD);
    const users = getUsersOfSchool(structure1);
    const broadcastGroup = getBroadcastGroup(broadcastGroupName, structure1);
    const students = users.filter(u => u.type === 'Student')
    const teacher = getRandomUserWithProfile(users, 'Teacher');
    const classId = teacher.classes[0].id
    const classUsers = students.filter(u => u.classes.map(c => c.id).indexOf(classId) >= 0);
    const firstStudent = getRandomUser(classUsers);
    const secondStudent = getRandomUser(classUsers, [firstStudent]);
    const thirdStudent = getRandomUser(classUsers, [firstStudent, secondStudent]);
    console.log("Teacher   - ", teacher.login);
    console.log("Student 1 - ", firstStudent.login);
    console.log("Student 2 - ", secondStudent.login);
    console.log("Student 3 - ", thirdStudent.login);
    // Teacher upload a file
    let teacherSession = <Session>authenticateWeb(teacher.login, 'password');
    const uploadedFile = uploadFile(fileToUpload);
    const fileId = uploadedFile._id;
    // Share this file to firstStudent as a manager
    const shares = {bookmarks: {}, groups: {}, users: {}}
    shares.users[firstStudent.id] = WS_MANAGER_SHARE;
    res = shareFile(fileId, shares);
    checkShareOk(res, 'teacher shares to first student')
    res = getShares(fileId)
    checkPresentShares(res, 'users', [firstStudent.id], 'first student appears in shares')
    // First student tries to share it to the list -> not shared because not visible
    let firstStudentSession = <Session>authenticateWeb(firstStudent.login, 'password');
    shares.groups[broadcastGroup.id] = WS_READER_SHARE;
    res = shareFile(fileId, shares)
    checkShareOk(res, 'student tries to share to broadcast group')
    res = getShares(fileId)
    checkAbsentShares(res, 'groups', [broadcastGroup.id], 'broadcast group does not appear in shares')
    // First student tries to share it to secondStudent -> ok
    delete shares.groups[broadcastGroup.id];
    shares.users[secondStudent.id] = WS_READER_SHARE;
    res = shareFile(fileId, shares)
    checkShareOk(res, 'student shares to second student')
    res = getShares(fileId)
    checkPresentShares(res, 'users', [secondStudent.id], 'second student appears in shares')
    // Teacher shares it with the list -> ok
    shares.groups[broadcastGroup.id] = WS_READER_SHARE;
    switchSession(teacherSession)
    res = shareFile(fileId, shares)
    checkShareOk(res, 'teacher shares to broadcast group')
    res = getShares(fileId)
    checkPresentShares(res, 'groups', [broadcastGroup.id], 'broadcast group appears in shares')
    // First student tries to share it to third student -> ok
    shares.users[thirdStudent.id] = WS_READER_SHARE;
    switchSession(firstStudentSession)
    res = shareFile(fileId, shares)
    checkShareOk(res, 'student shares to third student')
    res = getShares(fileId)
    checkPresentShares(res, 'users', [thirdStudent.id], 'third student appears in shares')
    // thrid student tries to remove share to the list -> ko
    delete shares.groups[broadcastGroup.id];
    authenticateWeb(thirdStudent.login, 'password')
    res = shareFile(fileId, shares)
    checkShareKo(res, 'non manager cannot remove shares')
  })
}

/**
 * Checks that if a user tries to share a resource via a sharebookmark then the share operation shares
 * the resource only to visible users, meaning that all the users of the sharebookmark who lost
 * visibility do not prevent the share AND do not raise an error.
 * 
 * Steps :
 * - Create 2 users, one teacher (whose visibility will remain) and one guest (who will lose visibility)
 * - ADMC makes guests visible to teachers
 * - With another teacher, create a share bookmark with the previous 2 users
 * - ADMC removes guests visibility by teachers
 * - The teacher shares a document with his bookmark
 * 
 * Checks :
 * - The teacher of the share bookmark is in the shares
 * - The guest is absent from the shares 
 */
function testSharesViaFavouritesWithUsersWhoBecameInvisible(data) {
  const { structure1 } = data;
  describe('[Workspace] Test shares via favourites and invisible users', () => {
    let res;
    const admcSession = authenticateWeb(__ENV.ADMC_LOGIN, __ENV.ADMC_PASSWORD);
    const users1 = getUsersOfSchool(structure1);
    const teacher1 = getRandomUserWithProfile(users1, 'Teacher');
    const newGuest = createUserAndGetData({
      firstName: 'New',
      lastName: 'Guest',
      type: 'Guest',
      structureId: structure1.id,
      birthDate: '1986-12-29',
      positionIds: []
    })
    const newTeacher = createUserAndGetData({
      firstName: 'New',
      lastName: 'Teacher',
      type: 'Teacher',
      structureId: structure1.id,
      birthDate: '1986-12-29',
      positionIds: []
    })
    console.log("Structure 1 - ", structure1.id)
    console.log("Teacher 1 - ", teacher1.login);
    console.log("Teacher 2 - ", newTeacher.login);
    console.log("Guest - ", newGuest.login);
    // Allow teachers to see guests
    const guestProfileGroup = getGuestRole(structure1);
    const teacherProfileGroup = getTeacherRole(structure1);
    res = addCommunicationBetweenGroups(teacherProfileGroup.id, guestProfileGroup.id)
    // Teacher creates a bookmark....
    const teacherSession = authenticateWeb(teacher1.login, 'password');
    const sbmCreationRequest: ShareBookMarkCreationRequest = {
      name: "Favori " + Date.now(),
      members: [newGuest.id, newTeacher.id]
    }
    const shareBookMark = createShareBookMarkOrFail(sbmCreationRequest)
    // Now ADMC removes the visibility of guests by teachers
    switchSession(admcSession);
    removeCommunicationBetweenGroups(teacherProfileGroup.id, guestProfileGroup.id)
    // The teacher uploads document....
    switchSession(teacherSession)
    const uploadedFile = uploadFile(fileToUpload);
    // ... shares it with her bookmark
    const fileId = uploadedFile._id;
    // Share this file to parents 2 as a manager -> ok
    const shares = {bookmarks: {}, groups: {}, users: {}}
    shares.bookmarks[shareBookMark.id] = WS_READER_SHARE
    res = shareFile(fileId, shares);
    checkShareOk(res, 'user can share document to share bookmark')
    res = getShares(fileId)
    checkPresentShares(res, "users", [newTeacher.id], "user of sharebookmark who is still visible appears in shares")
    checkAbsentShares(res, "users", [newGuest.id], "user of sharebookmark who is now invisible does not appear in shares")
  })
}
