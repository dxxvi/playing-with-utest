import { Pipe, PipeTransform } from '@angular/core';

@Pipe({
  name: 'moneyFormatter'
})
export class MoneyFormatterPipe implements PipeTransform {
  // transform a number to a string with 4 spaces before the decimal point and 2 digits after the decimal point
  transform(value: any, args?: any): string {
    if (Number.isNaN(value)) {
      return '       ';                                    // 7 spaces
    }
    const a: number = Math.floor(value);
    const b: number = Math.round(value * 100) - a*100;
    const stringA: string = (a < 10 ? '   ' : (a < 100 ? '  ' : (a < 1000 ? ' ' : ''))) + a;
    const stringB: string = (b < 10 ? '0' : '') + b;
    const result = stringA + '.' + stringB;
    console.log(`result: -${result}-`)
    return result;
  }
}
