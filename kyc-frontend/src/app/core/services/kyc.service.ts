import { inject, Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { KycRequest, KycSearchParams, Page, DocumentType } from '../models/models';

@Injectable({ providedIn: 'root' })
export class KycService {
  private http = inject(HttpClient);
  private base = `${environment.apiUrl}/kyc`;

  uploadDocument(userId: number, documentType: DocumentType, file: File, documentNumber: string): Observable<{ message: string; requestId: number }> {
    const form = new FormData();
    form.append('userId', userId.toString());
    form.append('documentType', documentType);
    form.append('file', file);
    form.append('documentNumber', documentNumber);
    return this.http.post<{ message: string; requestId: number }>(`${this.base}/upload`, form);
  }

  getLatestStatus(userId: number): Observable<KycRequest> {
    return this.http.get<KycRequest>(`${this.base}/status/${userId}`);
  }

  getAllStatus(userId: number): Observable<KycRequest[]> {
    return this.http.get<KycRequest[]>(`${this.base}/status/all/${userId}`);
  }

  searchRequests(params: KycSearchParams): Observable<Page<KycRequest>> {
    let httpParams = new HttpParams();
    if (params.userId) httpParams = httpParams.set('userId', params.userId);
    if (params.userName) httpParams = httpParams.set('userName', params.userName);
    if (params.status) httpParams = httpParams.set('status', params.status);
    if (params.documentType) httpParams = httpParams.set('documentType', params.documentType);
    if (params.dateFrom) httpParams = httpParams.set('dateFrom', params.dateFrom);
    if (params.dateTo) httpParams = httpParams.set('dateTo', params.dateTo);
    httpParams = httpParams.set('page', params.page ?? 0);
    httpParams = httpParams.set('size', params.size ?? 10);
    return this.http.get<Page<KycRequest>>(`${this.base}/search`, { params: httpParams });
  }

  triggerReport(dateFrom?: string, dateTo?: string, email?: string): Observable<string> {
    let httpParams = new HttpParams();
    if (dateFrom) httpParams = httpParams.set('dateFrom', dateFrom);
    if (dateTo) httpParams = httpParams.set('dateTo', dateTo);
    if (email) httpParams = httpParams.set('email', email);
    return this.http.post(`${this.base}/report`, {}, { params: httpParams, responseType: 'text' });
  }
}
