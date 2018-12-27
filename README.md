# Change padding in Bootstrap
Must use SCSS

Find where the paddings are specified with `find node_modules/bootstrap/scss -type f -exec grep --with-filename "btn-padding-y" "{}" \;`

They are defined in _variables.scss