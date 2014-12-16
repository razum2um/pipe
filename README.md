# Pipe

A developer-happiness tool, which allows you to write "middlewares for data" in request-response cycle

## Usage

Assume we have some web service (assume SOAP like this: `http://wsf.cdyne.com/WeatherWS/Weather.asmx?WSDL`)

And we have a client which sends a compressed request:

    <soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/" xmlns:weat="http://ws.cdyne.com/WeatherWS/"><soapenv:Header/><soapenv:Body><weat:GetCityWeatherByZIP><weat:ZIP>10007</weat:ZIP></weat:GetCityWeatherByZIP></soapenv:Body></soapenv:Envelope>

Now we want:

- debug requests and responses
- cache responses for identical requests for rapid development

It can be achieved like this:

    user=> (pipe.core/start "wsf.cdyne.com" [(partial pipe.core/pretty-xml 4) (pipe.core/prefix-log "Formatted:")])

This launches proxy on `localhost:8080` and calling same action with same payload will log request and response with proper indent and also cache identical queries

    2014-Dec-17 04:44:02 +0700 macpro.local INFO [pipe.core] - Formatted: <?xml version="1.0" encoding="UTF-8"?><soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/" xmlns:weat="http://ws.cdyne.com/WeatherWS/">
        <soapenv:Header/>
        <soapenv:Body>
            <weat:GetCityWeatherByZIP>
                <weat:ZIP>10007</weat:ZIP>
            </weat:GetCityWeatherByZIP>
        </soapenv:Body>
    </soapenv:Envelope>

    2014-Dec-17 04:44:03 +0700 macpro.local INFO [pipe.core] - Formatted: <?xml version="1.0" encoding="UTF-8"?><soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:xsd="http://www.w3.org/2001/XMLSchema">
        <soap:Body>
            <GetCityWeatherByZIPResponse xmlns="http://ws.cdyne.com/WeatherWS/">
                <GetCityWeatherByZIPResult>
                    <Success>true</Success>
                    <ResponseText>City Found</ResponseText>
                    <State>NY</State>
                    <City>New York</City>
                    <WeatherStationCity>White Plains</WeatherStationCity>
                    <WeatherID>15</WeatherID>
                    <Description>N/A</Description>
                    <Temperature>63</Temperature>
                    <RelativeHumidity>87</RelativeHumidity>
                    <Wind>E7</Wind>
                    <Pressure>29.97S</Pressure>
                    <Visibility/>
                    <WindChill/>
                    <Remarks/>
                </GetCityWeatherByZIPResult>
            </GetCityWeatherByZ...

What happened:

- request was piped through a middleware `pipe.core/pretty-xml` which intends it
- request went throgh `pipe.core/prefix-log` which dumped pretty request truncated as above

- pretty request went to the webservice

- response was piped through a middleware `pipe.core/pretty-xml` which intends it
- response went throgh `pipe.core/prefix-log` which dumped pretty response truncated as above

Beware, the proxy server is asynchronous and in the end proxy can be stopped like this:

    user=> (pipe.core/stop)

## Middlewares

A middleware is a vector of 2 functions the first is applied to the request, the second to the response.

If you'd like to modify only the response you can use `[identity response-fn]`
or a helper `(pipe.core/response-chain response-fn)`

Same for middlewares for only requests: `[request-fn identity]` or `(pipe.core/request-chain response-fn)`

Middlewares are appried left-to-right as they follow in the list.

A middleware can be a function alone - in thos case it gets applied to both request and response

## License

Copyright © 2014 Vlad Bokov

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.

