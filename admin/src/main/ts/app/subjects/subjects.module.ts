import {CommonModule} from '@angular/common'
import {FormsModule} from '@angular/forms'
import {RouterModule} from '@angular/router'
import {NgModule} from '@angular/core'
import {SijilModule} from 'sijil'
import {UxModule} from '../shared/ux/ux.module'
import {routes} from './subjects.routing'
import {SubjectsComponent} from './subjects.component'
import {SubjectsStore} from "./subjects.store";
import {SubjectCreate} from "./create/subject-create.component";
import {SubjectsResolver} from "./subjects.resolver";
import {SubjectDetails} from "./details/subject-details.component";
import {SubjectsService} from "./subjects.service";


@NgModule({
    imports: [
        CommonModule,
        FormsModule,
        UxModule,
        SijilModule.forChild(),
        RouterModule.forChild(routes)
    ],
    declarations: [
        SubjectsComponent,
        SubjectCreate,
        SubjectDetails
    ],
    exports: [
        RouterModule
    ],
    providers: [
        SubjectsResolver,
        SubjectsStore,
        SubjectsService
    ]
})
export class SubjectsModule {
}