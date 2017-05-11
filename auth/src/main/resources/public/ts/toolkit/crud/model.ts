import http from 'axios'

import { AbstractModel } from './abstract.model'
import { CrudApi } from './abstract.crud'
import { Http } from '../http.interface'
import { mixCasts } from '../minicast'

export class Model<T> extends AbstractModel<T> {

    protected http: Http = http as any

    constructor(api: CrudApi, childrenCasts?: mixCasts) {
        super(api, childrenCasts)
    }

}