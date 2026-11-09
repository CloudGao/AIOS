param()
function Invoke-AiosTest {
    param([Parameter(Mandatory=$true)] [hashtable] $Request, [int] $Port=4884)
    $client=[System.Net.Sockets.TcpClient]::new('127.0.0.1',$Port)
    try {
        $client.ReceiveTimeout=45000
        $stream=$client.GetStream()
        $writer=[System.IO.StreamWriter]::new($stream,[System.Text.UTF8Encoding]::new($false))
        $writer.AutoFlush=$true
        $reader=[System.IO.StreamReader]::new($stream)
        $writer.WriteLine(($Request | ConvertTo-Json -Depth 30 -Compress))
        $line=$reader.ReadLine()
        if(!$line){throw 'AIOS debug connection closed without a response'}
        return ($line | ConvertFrom-Json)
    } finally {$client.Dispose()}
}
