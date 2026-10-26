import { TestBed } from '@angular/core/testing';
import {
  HTTP_INTERCEPTORS,
  HttpClient,
  provideHttpClient,
  withInterceptorsFromDi,
} from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { API_CONFIG } from './api.config';
import { ApiInterceptor } from './api.interceptor';

describe('API interceptor credential isolation', () => {
  let http: HttpTestingController;
  let client: HttpClient;
  beforeEach(() => {
    sessionStorage.clear();
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptorsFromDi()),
        provideHttpClientTesting(),
        { provide: HTTP_INTERCEPTORS, useClass: ApiInterceptor, multi: true },
      ],
    });
    http = TestBed.inject(HttpTestingController);
    client = TestBed.inject(HttpClient);
  });
  afterEach(() => {
    http.verify();
    sessionStorage.clear();
  });
  it('prefixes backend paths and attaches the current token', () => {
    sessionStorage.setItem('accessToken', 'test-token');
    client.get('/api/accounts').subscribe();
    const req = http.expectOne(`${API_CONFIG.baseUrl}/api/accounts`);
    expect(req.request.headers.get('Authorization')).toBe('Bearer test-token');
    req.flush([]);
  });
  it('sends no bearer header after logout', () => {
    client.get('/api/accounts').subscribe();
    const req = http.expectOne(`${API_CONFIG.baseUrl}/api/accounts`);
    expect(req.request.headers.has('Authorization')).toBe(false);
    req.flush([]);
  });
});
