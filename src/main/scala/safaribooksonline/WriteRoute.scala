package safaribooksonline

import spark.{Request, Response}

class WriteRoute extends spark.Route {
    override def handle(request: Request, response: Response): AnyRef = {
        response.`type`("application/json")
        Data("text area", "wget img")
    }
}
