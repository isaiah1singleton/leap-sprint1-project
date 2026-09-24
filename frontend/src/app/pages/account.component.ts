import { Component, inject } from '@angular/core';
import { AuthService } from '../core/auth.service';

@Component({
  selector: 'app-account',
  template: `
    <div class="row">
      <div class="card card-pad-lg" style="flex:1">
        <h2 class="card-title">Profile</h2>
        <dl style="margin:0">
          <div class="definition"><dt>Email</dt><dd><strong>{{ auth.currentUser() }}</strong></dd></div>
          <div class="definition"><dt>Account type</dt><dd>Individual investor</dd></div>
          <div class="definition"><dt>Account number</dt><dd>IT8-4417-2290</dd></div>
          <div class="definition"><dt>Base currency</dt><dd>USD</dd></div>
          <div class="definition"><dt>Verification</dt><dd class="gain">Verified</dd></div>
          <div class="definition"><dt>Linked bank account</dt><dd>••••4417</dd></div>
        </dl>
      </div>
      <div class="card card-pad-lg" style="width:250px;flex:none;display:flex;flex-direction:column">
        <h2 class="card-title">Security</h2>
        <button class="btn btn-secondary btn-sm">Change password</button>
        <button class="btn btn-secondary btn-sm">Set up two-factor</button>
        <p style="border-top:1px solid var(--line-soft);padding-top:12px;margin-top:12px;font-size:12px;color:var(--muted);line-height:1.5">Trading limits and permissions are set by your administrator.</p>
      </div>
    </div>
  `,
})
export class AccountComponent {
  readonly auth = inject(AuthService);
}
