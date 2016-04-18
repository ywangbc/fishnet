set title "Window Size"
set xlabel "Time"
set ylabel "WinSize"
set xtics 4000
set grid
set terminal png size 800,600 enhanced font "Helvetica,20"
set output 'winsize.png'
plot "winsize.txt"
