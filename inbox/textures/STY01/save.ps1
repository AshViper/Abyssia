param($s); $t = Get-Clipboard -Raw; [IO.File]::WriteAllText("F:\Java\Abyssia\inbox\textures\STY01\$s.txt", $t); python F:/Java/Abyssia/inbox/textures/STY01/decode.py $s
