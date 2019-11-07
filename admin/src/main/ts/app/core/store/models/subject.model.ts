import {Model} from 'entcore-toolkit';

export class SubjectModel extends Model<SubjectModel> {

    id?: string;
    label?: string;
    code?: string;
    manual?: string;
    structureId?: string;

    constructor() {
        super({
            create: '/directory/subject'
        });
    }

    toJSON() {
        let back =  {
            id: this.id,
            label: this.label,
            code: this.code,
            structureId: this.structureId
        };
        if(this.manual){
            back['manual'] = this.manual;
        }
        return back;
    }
}