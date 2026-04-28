import { inject, Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { User, UserUpdate, UserSearchParams, Page } from '../models/models';

@Injectable({ providedIn: 'root' })
export class UserService {
  private http = inject(HttpClient);
  private base = `${environment.apiUrl}/users`;

  getAllUsers(): Observable<User[]> {
    return this.http.get<User[]>(this.base);
  }

  getUserById(userId: number): Observable<User> {
    return this.http.get<User>(`${this.base}/${userId}`);
  }

  updateUser(userId: number, dto: UserUpdate): Observable<User> {
    return this.http.patch<User>(`${this.base}/${userId}`, dto);
  }

  deleteUser(userId: number): Observable<string> {
    return this.http.delete(`${this.base}/${userId}`, { responseType: 'text' });
  }

  searchUsers(params: UserSearchParams): Observable<Page<User>> {
    let httpParams = new HttpParams();
    if (params.name) httpParams = httpParams.set('name', params.name);
    if (params.email) httpParams = httpParams.set('email', params.email);
    if (params.mobileNumber) httpParams = httpParams.set('mobileNumber', params.mobileNumber);
    if (params.isActive !== undefined) httpParams = httpParams.set('isActive', params.isActive);
    httpParams = httpParams.set('page', params.page ?? 0);
    httpParams = httpParams.set('size', params.size ?? 10);
    return this.http.get<Page<User>>(`${this.base}/search`, { params: httpParams });
  }
}
