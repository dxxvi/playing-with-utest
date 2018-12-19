import {NgModule} from '@angular/core';
import {MoneyFormatterPipe} from "./money-formatter.pipe";

@NgModule({
  imports:        [],
  declarations:   [MoneyFormatterPipe],
  exports:        [MoneyFormatterPipe],
})
export class PipeModule {
  static forRoot() {
    return {
      ngModule: PipeModule,
      providers: [],
    };
  }
}
