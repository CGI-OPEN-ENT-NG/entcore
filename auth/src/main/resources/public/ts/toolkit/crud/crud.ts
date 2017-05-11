import http from 'axios'

import { Http } from '../http.interface'
import { AbstractCrud } from './abstract.crud'

export class Crud<T> extends AbstractCrud<T> {

    protected http: Http = http as any

}