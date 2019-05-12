import { BrowserModule } from '@angular/platform-browser';
import { NgModule } from '@angular/core';

import { AppComponent } from './app.component';
import { HTTP_INTERCEPTORS, HttpClientModule } from '@angular/common/http';
import { AkkaInterceptor } from './akka-interceptor';

@NgModule({
  declarations: [
    AppComponent
  ],
  imports: [
    BrowserModule, HttpClientModule
  ],
  providers: [
    { provide: HTTP_INTERCEPTORS, useClass: AkkaInterceptor, multi: true }
  ],
  bootstrap: [AppComponent]
})
export class AppModule { }
