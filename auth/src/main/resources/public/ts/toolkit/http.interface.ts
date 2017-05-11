export interface HttpResponse {
    data: any,
    status: number,
    statusText: string,
    headers: {},
    config: {}
}

export interface Http {
    get(url: string, opts?: Object) : Promise<HttpResponse>
    post(url: string, data: Object, opts?:Object) : Promise<HttpResponse>
    postFile(url: string, data: FormData, opts?: Object) : Promise<HttpResponse>
    put(url: string, data?: Object, opts?: Object) : Promise<HttpResponse>
    putFile(url: string, data: FormData, opts?: Object) : Promise<HttpResponse>
    delete(url: string, opts?: Object) : Promise<HttpResponse>
}