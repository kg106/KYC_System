import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';

@Component({
  selector: 'app-auth-layout',
  standalone: true,
  imports: [RouterOutlet],
  template: `
    <div class="auth-shell">
      <div class="auth-brand">
        <div class="brand-logo">
          <span class="material-icons">verified_user</span>
        </div>
        <h1 class="brand-name">KYC<span class="accent">System</span></h1>
        <p class="brand-tagline">Secure · Compliant · Intelligent</p>
      </div>
      <div class="auth-card fade-in-up">
        <router-outlet />
      </div>
    </div>
  `,
  styles: [`
    .auth-shell {
      min-height: 100vh;
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      padding: 24px;
      background:
        radial-gradient(ellipse 80% 50% at 50% -20%, rgba(92,107,192,0.25), transparent),
        radial-gradient(ellipse 60% 40% at 80% 80%, rgba(0,229,255,0.08), transparent),
        var(--surface-0);
    }
    .auth-brand {
      text-align: center;
      margin-bottom: 28px;
    }
    .brand-logo {
      width: 56px; height: 56px;
      border-radius: 16px;
      background: linear-gradient(135deg, var(--primary), var(--accent));
      display: inline-flex; align-items: center; justify-content: center;
      margin-bottom: 12px;
      box-shadow: 0 8px 32px rgba(92,107,192,0.45);
      .material-icons { font-size: 28px; color: #fff; }
    }
    .brand-name {
      font-size: 2rem; font-weight: 800; letter-spacing: -1px;
      color: var(--text-primary);
    }
    .accent { color: var(--accent); }
    .brand-tagline { color: var(--text-secondary); font-size: 0.85rem; margin-top: 4px; }
    .auth-card {
      width: 100%; max-width: 460px;
      background: var(--surface-1);
      border: 1px solid var(--border);
      border-radius: var(--radius-lg);
      padding: 40px;
      box-shadow: 0 24px 64px rgba(0,0,0,0.6);
    }
    @media(max-width: 500px) {
      .auth-card { padding: 28px 20px; }
    }
  `]
})
export class AuthLayoutComponent {}
