"""Transport-neutral POST dispatcher. Requires a trusted HTTPS host; not a listener."""
import base64
import binascii
import json
import re

MAX_BODY = 4096

def unique_object(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            raise ValueError("duplicate key")
        result[key] = value
    return result

def decode_binary(value, maximum):
    if not isinstance(value,str) or len(value)>4*((maximum+2)//3):
        raise ValueError("invalid binary")
    raw = base64.b64decode(value,validate=True)
    if not 1<=len(raw)<=maximum or base64.b64encode(raw).decode("ascii")!=value:
        raise ValueError("invalid binary")
    return raw

def dispatch(service, route, body, authorization=None):
    if not isinstance(body,bytes) or len(body)>MAX_BODY:
        return 413, {"error":"INVALID_REQUEST"}
    try:
        data = json.loads(body.decode("utf-8"),object_pairs_hook=unique_object)
        if not isinstance(data,dict):
            raise ValueError("invalid object")
        if route == "/v1/auth/logout":
            if data or not isinstance(authorization,str) or not re.fullmatch(r"Bearer [0-9a-f]{64}",authorization):
                raise ValueError("invalid logout")
            service.logout(authorization[7:])
            return 200, {"result":"APPLIED"}
        if route.startswith("/v1/auth/admin/"):
            return dispatch_admin(service,route,data,authorization)
        if route not in ("/v1/auth/register","/v1/auth/login","/v1/auth/device-challenge"):
            return 404, {"error":"NOT_FOUND"}
        expected = {"username","password"}
        if route.endswith("/device-challenge"):
            expected.add("key_digest")
        if route.endswith("/login") and "proof" in data:
            expected.add("proof")
        if set(data)!=expected:
            raise ValueError("invalid fields")
        username,password = data["username"],data["password"]
        if not isinstance(username,str) or not isinstance(password,str) or len(username)>32 or len(password)>128:
            raise ValueError("invalid credentials")
        if route.endswith("/register"):
            return 200, {"result":service.register(username,password)}
        if route.endswith("/device-challenge"):
            key = data["key_digest"]
            if not isinstance(key,str) or not re.fullmatch(r"[0-9a-f]{64}",key):
                raise ValueError("invalid digest")
            result = service.device_challenge(username,password,key)
        else:
            proof = data.get("proof")
            if "proof" in data:
                if not isinstance(proof,dict) or set(proof)!={"key_digest","nonce","public_der","signature"}:
                    raise ValueError("invalid proof")
                for field in ("key_digest","nonce"):
                    if not isinstance(proof[field],str) or not re.fullmatch(r"[0-9a-f]{64}",proof[field]):
                        raise ValueError("invalid proof")
                proof["public_der"] = decode_binary(proof["public_der"],256)
                proof["signature"] = decode_binary(proof["signature"],80)
            result = service.login(username,password,proof)
        return (200,result) if result is not None else (401,{"error":"DENIED"})
    except (ValueError,TypeError,UnicodeError,binascii.Error,RecursionError):
        return 400, {"error":"INVALID_REQUEST"}


def dispatch_admin(service, route, data, authorization):
    if not isinstance(authorization,str) or not re.fullmatch(r"Bearer [0-9a-f]{64}",authorization):
        return 401, {"error":"DENIED"}
    token = authorization[7:]
    action = route.removeprefix("/v1/auth/admin/")
    if action == "list-users":
        if set(data) != {"after","limit"}:
            raise ValueError("invalid fields")
        after, limit = data["after"], data["limit"]
        if type(limit) is not int or not 1<=limit<=20:
            raise ValueError("invalid limit")
        if after is not None and (not isinstance(after,str) or not re.fullmatch(r"[0-9a-f]{32}",after)):
            raise ValueError("invalid cursor")
        result = service.list_users(token,after,limit)
        return (200,result) if result is not None else (403,{"error":"DENIED"})
    fields = {
        "review":{"target","expected","desired"},
        "grant-license":{"target","kind","expires"},
        "revoke-license":{"target"},
        "bind-device":{"target","key_digest","expected"},
    }
    if action not in fields:
        return 404, {"error":"NOT_FOUND"}
    if set(data)!=fields[action]:
        raise ValueError("invalid fields")
    target = data["target"]
    if not isinstance(target,str) or not re.fullmatch(r"[0-9a-f]{32}",target):
        raise ValueError("invalid target")
    if action == "review":
        if not all(isinstance(data[k],str) for k in ("expected","desired")):
            raise ValueError("invalid status")
        result = service.review(token,target,data["expected"],data["desired"])
    elif action == "grant-license":
        if not isinstance(data["kind"],str) or (data["expires"] is not None and type(data["expires"]) is not int):
            raise ValueError("invalid license")
        result = service.grant_license(token,target,data["kind"],data["expires"])
    elif action == "revoke-license":
        result = service.revoke_license(token,target)
    else:
        for value in (data["key_digest"],data["expected"]):
            if value is not None and (not isinstance(value,str) or not re.fullmatch(r"[0-9a-f]{64}",value)):
                raise ValueError("invalid digest")
        if data["key_digest"] is None:
            raise ValueError("missing digest")
        result = service.bind_device(token,target,data["key_digest"],data["expected"])
    status = {"APPLIED":200,"DENIED":403,"CONFLICT":409,"UNAVAILABLE":503}.get(result,503)
    return status, {"result":result}
