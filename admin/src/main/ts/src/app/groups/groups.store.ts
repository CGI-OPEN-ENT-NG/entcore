import { AbstractStore } from '../core/store/abstract.store';
import { ClassModel, StructureModel } from '../core/store/models/structure.model';
import { GroupModel } from '../core/store/models/group.model';
import { Injectable } from "@angular/core";

@Injectable()
export class GroupsStore extends AbstractStore {

    constructor() {
        super(['structure', 'group', 'class']);
    }

    structure: StructureModel;
    group: GroupModel;
    class: ClassModel;
}
