import { BrowserModule } from '@angular/platform-browser';
import { NgModule } from '@angular/core';
import { FormsModule } from '@angular/forms';

import { AppComponent } from './app.component';
import { StockComponent } from './stock.component';
import { ConfigurationComponent } from './configuration.component';
import {BrowserAnimationsModule, NoopAnimationsModule} from '@angular/platform-browser/animations';
import {PipeModule} from './pipe.module';
import { HighchartsChartModule } from 'highcharts-angular';
import { NoticeComponent } from './notice/notice.component';

@NgModule({
  declarations: [
    AppComponent,
    StockComponent,
    NoticeComponent,
    ConfigurationComponent
  ],
  imports: [
    BrowserModule, FormsModule,
    BrowserAnimationsModule, PipeModule.forRoot(), HighchartsChartModule
  ],
  providers: [],
  bootstrap: [AppComponent]
})
export class AppModule { }
