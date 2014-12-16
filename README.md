# pipe

Allows you to write "middlewares for data"

## Usage

This logs raw input xml, indents it and logs formatted response as well

    user=> (pipe.core/start "localhost:8000" (pipe.core/prefix-log "Original:") pipe.core/pretty-xml (pipe.core/prefix-log "Processed:"))

## License

Copyright © 2014 Vlad Bokov

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
