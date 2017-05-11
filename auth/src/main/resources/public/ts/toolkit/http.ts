import http from 'axios';
import { Http as h } from './http.interface';

export let Req: h = http as any;
export { HttpResponse } from './http.interface';