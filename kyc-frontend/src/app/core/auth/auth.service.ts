import { inject, Injectable, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Router } from '@angular/router';
import { Observable, tap } from 'rxjs';
import { JwtAuthResponse, LoginRequest, PasswordResetDTO, PasswordResetRequestDTO, User } from '../models/models';
import { environment } from '../../../environments/environment';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private http = inject(HttpClient);
  private router = inject(Router);

  private _accessToken = signal<string | null>(localStorage.getItem('access_token'));
  private _currentUser = signal<User | null>(null);

  readonly accessToken = this._accessToken.asReadonly();
  readonly currentUser = this._currentUser.asReadonly();

  constructor() {
    if (this._accessToken()) {
      this.loadCurrentUser();
    }
  }

  login(credentials: LoginRequest): Observable<JwtAuthResponse> {
    return this.http
      .post<JwtAuthResponse>(`${environment.apiUrl}/auth/login`, credentials, { withCredentials: true })
      .pipe(
        tap(res => {
          localStorage.setItem('access_token', res.accessToken);
          this._accessToken.set(res.accessToken);
          this.loadCurrentUser();
        })
      );
  }

  register(user: User): Observable<User> {
    return this.http.post<User>(`${environment.apiUrl}/auth/register`, user);
  }

  logout(): void {
    this.http
      .post(`${environment.apiUrl}/auth/logout`, {}, {
        withCredentials: true,
        responseType: 'text'
      })
      .subscribe({
        complete: () => this.clearSession(),
        error: (e) => {
          console.error('Logout failed', e);
          this.clearSession();
        }
      });
  }

  forgotPassword(dto: PasswordResetRequestDTO): Observable<string> {
    return this.http.post(`${environment.apiUrl}/auth/forgot-password`, dto, {
      responseType: 'text'
    });
  }

  resetPassword(dto: PasswordResetDTO): Observable<string> {
    return this.http.post(`${environment.apiUrl}/auth/change-password`, dto, {
      responseType: 'text'
    });
  }

  refreshToken(): Observable<JwtAuthResponse> {
    return this.http
      .post<JwtAuthResponse>(`${environment.apiUrl}/auth/refresh`, {}, { withCredentials: true })
      .pipe(tap(res => {
        localStorage.setItem('access_token', res.accessToken);
        this._accessToken.set(res.accessToken);
        this.loadCurrentUser();
      }));
  }

  private loadCurrentUser(): void {
    const token = this._accessToken();
    if (!token) return;
    try {
      const payload = JSON.parse(atob(token.split('.')[1]));
      this._currentUser.set({
        id: payload.userId,
        name: payload.sub ?? payload.name ?? '',
        email: payload.sub ?? '',
        mobileNumber: '',
        tenantId: payload.tenantId,
      });
      // Store role list on user for easy access
      (this._currentUser() as any).roles = payload.roles ?? [];
    } catch { /* ignore */ }
  }

  getRoles(): string[] {
    const token = this._accessToken();
    if (!token) return [];
    try {
      const payload = JSON.parse(atob(token.split('.')[1]));
      return payload.roles ?? [];
    } catch { return []; }
  }

  hasRole(role: string): boolean {
    return this.getRoles().includes(`ROLE_${role}`);
  }

  isLoggedIn(): boolean {
    return !!this._accessToken();
  }

  setAccessToken(token: string): void {
    localStorage.setItem('access_token', token);
    this._accessToken.set(token);
    this.loadCurrentUser();
  }

  private clearSession(): void {
    localStorage.removeItem('access_token');
    this._accessToken.set(null);
    this._currentUser.set(null);
    this.router.navigate(['/auth/login']);
  }
}
