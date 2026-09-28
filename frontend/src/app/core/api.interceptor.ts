import { Injectable } from '@angular/core';
import {
  HttpRequest,
  HttpHandler,
  HttpEvent,
  HttpInterceptor
} from '@angular/common/http';
import { Observable } from 'rxjs';
import { API_CONFIG } from './api.config';

/**
 * HTTP Interceptor that automatically prepends the API base URL to all requests.
 * This allows frontend services to use relative paths like '/api/auth/sign-in'
 * and have them automatically resolved to the full URL.
 */
@Injectable()
export class ApiInterceptor implements HttpInterceptor {
  intercept(request: HttpRequest<unknown>, next: HttpHandler): Observable<HttpEvent<unknown>> {
    // Only prepend base URL to requests that don't already have a full URL
    if (!request.url.startsWith('http://') && !request.url.startsWith('https://')) {
      request = request.clone({
        url: `${API_CONFIG.baseUrl}${request.url}`
      });
    }
    return next.handle(request);
  }
}
