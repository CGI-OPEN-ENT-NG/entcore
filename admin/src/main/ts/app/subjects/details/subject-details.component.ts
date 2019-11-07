import {ChangeDetectionStrategy, ChangeDetectorRef, Component, OnDestroy, OnInit} from "@angular/core";
import {HttpClient} from "@angular/common/http";
import {SubjectsStore} from "../subjects.store";
import {NotifyService, SpinnerService} from "../../core/services";
import {ActivatedRoute, Router} from "@angular/router";
import {SubjectModel} from "../../core/store/models";

@Component({
    selector: 'subject-details',
    templateUrl: './subject-details.component.html',
    changeDetection: ChangeDetectionStrategy.OnPush
})

export class SubjectDetails implements OnInit, OnDestroy {

    public subject:SubjectModel;

    constructor(private http: HttpClient,
                public subjectsStore: SubjectsStore,
                private ns: NotifyService,
                private spinner: SpinnerService,
                private router: Router,
                private route: ActivatedRoute,
                private cdRef: ChangeDetectorRef) {
    }



    ngOnInit(): void {
        this.route.params.subscribe(params => {
            this.subjectsStore.subject = null;
            let id = params["subjectId"];
            this.subjectsStore.subject = this.subject = this.subjectsStore.structure.subjects.data.find(sub => sub.id === id);
            this.cdRef.markForCheck();
        });

    }

    ngOnDestroy(): void {
    }

}
