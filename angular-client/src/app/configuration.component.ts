import {Component, ElementRef, OnInit} from '@angular/core';

@Component({
  selector: 'div.configuration',
  templateUrl: './configuration.component.html',
  styleUrls: ['./configuration.component.css']
})
export class ConfigurationComponent implements OnInit {
  open = false;
  addedSymbols: string;
  removedSymbols: string;

  constructor(private el: ElementRef) { }

  ngOnInit() {
  }

  clickConfiguration() {
    this.open = !this.open;
    this.el.nativeElement.style.position = this.open ? 'static' : 'fixed';
  }

  addSymbols() {}

  removeSymbols() {}
}
