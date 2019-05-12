import { Injectable } from '@angular/core';
import { HttpEvent, HttpInterceptor, HttpHandler, HttpRequest } from '@angular/common/http';
import { Observable } from 'rxjs';

/**
 * I think Akka writes this cookie paradoxGroups: {"language":"group-scala"} to the client. So when we send a request,
 * Akka receives it and then throws a warning: akka.actor.ActorSystemImpl - Illegal header: Illegal 'cookie' header:
 * Cookie header contained no parsable cookie values
 * This interceptor removes that cookie in every request.
 */
@Injectable()
export class AkkaInterceptor implements HttpInterceptor {
    intercept(req: HttpRequest<any>, next: HttpHandler): Observable<HttpEvent<any>> {
/*
        const cookie = req.headers.get('Cookie');
        console.dir(cookie);           // cookie is null here. It seems that we cannot modify the Cookie
*/
        return next.handle(req);
    }
}