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
 * HTTP Interceptor that:
 * 1. Prepends the API base URL to all requests
 * 2. Attaches the Bearer token from sessionStorage for authenticated requests
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

    // Attach Bearer token if available
    const token = sessionStorage.getItem('accessToken');
    if (token) {
      request = request.clone({
        setHeaders: {
          Authorization: `Bearer ${token}`
        }
      });
    }

    return next.handle(request);
  }
}
