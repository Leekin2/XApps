# Preserve the source bytes; edit only one unambiguous HOTWORD attribute.
BEGIN { RS="\034"; ORS="" }
function spaces(n, s) { s=""; while(n-->0) s=s " "; return s }
function uncomment(s, p,q) {
    while ((p=index(s,"<!--"))>0) {
        q=index(substr(s,p+4),"-->"); if (!q) return ""
        q=p+q+5
        s=substr(s,1,p-1) spaces(q-p+1) substr(s,q+1)
    }
    return s
}
{
    raw=$0; clean=uncomment(raw); rest=clean; consumed=0; targets=0
    while (match(rest,/<stream_config[ \t\r\n]*>/)) {
        start=consumed+RSTART; tail=substr(clean,start)
        closing=index(tail,"</stream_config>"); if (!closing) exit 2
        lengthBlock=closing+length("</stream_config>")-1
        block=substr(clean,start,lengthBlock)
        if (block ~ /<name>[ \t\r\n]*HOTWORD_VOICE_UI[ \t\r\n]*<\/name>/) {
            targets++
            attrs=block; count=0
            while (match(attrs,/get_module_version[ \t\r\n]*=/)) {
                count++; attrs=substr(attrs,RSTART+RLENGTH)
            }
            if (count!=1 || !match(block,/get_module_version[ \t\r\n]*=[ \t\r\n]*["\047](false|true)["\047]/)) exit 3
            attribute=substr(block,RSTART,RLENGTH); offset=start+RSTART-1
            match(attribute,/["\047](false|true)["\047]$/)
            valueStart=offset+RSTART; valueLength=RLENGTH-2
        }
        consumed=start+lengthBlock-1; rest=substr(clean,consumed+1)
    }
    if (targets!=1) exit 4
    printf "%s",substr(raw,1,valueStart-1) "true" substr(raw,valueStart+valueLength)
}
