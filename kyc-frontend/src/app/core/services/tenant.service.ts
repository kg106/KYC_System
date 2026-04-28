import { inject, Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Tenant, TenantCreate, TenantUpdate, TenantStats, Page } from '../models/models';

@Injectable({ providedIn: 'root' })
export class TenantService {
  private http = inject(HttpClient);
  private base = `${environment.apiUrl}/tenants`;

  createTenant(dto: TenantCreate): Observable<Tenant> {
    return this.http.post<Tenant>(this.base, dto);
  }

  getAllTenants(page = 0, size = 10): Observable<Page<Tenant>> {
    const params = new HttpParams().set('page', page).set('size', size);
    return this.http.get<Page<Tenant>>(this.base, { params });
  }

  getTenant(tenantId: string): Observable<Tenant> {
    return this.http.get<Tenant>(`${this.base}/${tenantId}`);
  }

  updateTenant(tenantId: string, dto: TenantUpdate): Observable<Tenant> {
    return this.http.patch<Tenant>(`${this.base}/${tenantId}`, dto);
  }

  activate(tenantId: string): Observable<string> {
    return this.http.patch(`${this.base}/${tenantId}/activate`, {}, { responseType: 'text' });
  }

  deactivate(tenantId: string): Observable<string> {
    return this.http.patch(`${this.base}/${tenantId}/deactivate`, {}, { responseType: 'text' });
  }

  rotateApiKey(tenantId: string): Observable<{ apiKey: string }> {
    return this.http.post<{ apiKey: string }>(`${this.base}/${tenantId}/rotate-api-key`, {});
  }

  getStats(tenantId: string): Observable<TenantStats> {
    return this.http.get<TenantStats>(`${this.base}/${tenantId}/stats`);
  }
}
